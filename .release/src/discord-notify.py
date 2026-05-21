#!/usr/bin/env python3
"""Discord notification for plugin releases. Reads config from env vars and YAML."""
import os, sys, json
from datetime import datetime, timezone

try:
    import yaml
except ImportError:
    print("PyYAML not installed, trying pip...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pyyaml"])
    import yaml

def main():
    webhook_url = os.environ.get('DISCORD_WEBHOOK_URL', '')
    if not webhook_url:
        print("No DISCORD_WEBHOOK_URL set, skipping")
        return

    version = os.environ['VERSION']
    version_number = version[1:] if version.startswith('v') else version
    release_tag = version if version.startswith('v') else f"v{version}"
    display_version = release_tag
    repo_name = os.environ.get('REPO_NAME', 'unknown')
    repo_owner = os.environ.get('REPO_OWNER', 'castledking')

    payload = {}

    config_path = os.environ.get('DISCORD_CONFIG_PATH', '.release/discord-updater-webhook.yml')
    config = {}
    if os.path.exists(config_path):
        with open(config_path) as f:
            config = yaml.safe_load(f) or {}

    if 'message' in config:
        msg = config['message']
        if msg.get('username'): payload['username'] = msg['username']
        if msg.get('avatar_url'): payload['avatar_url'] = msg['avatar_url']
        if msg.get('content'): payload['content'] = msg['content']

    embed = {}
    use_short = config.get('use-short-md', False)
    notes_suffix = '-SHORT' if use_short else ''
    # Files live in .release/latest/ in the repo; CI copies them to /tmp/release-notes/
    # for the Modrinth/Spigot scripts. Prefer the in-repo path so Discord works without
    # the workflow having to copy/mount anything.
    notes_path = os.environ.get('RELEASE_NOTES_PATH', f".release/latest/v{version_number}{notes_suffix}.md")
    if os.path.exists(notes_path):
        with open(notes_path) as f:
            lines = f.readlines()
        embed['title'] = lines[0].strip() if lines else f"{repo_name} {display_version}"
        body = ''.join(lines[1:]).strip()
        if len(body) > 4000: body = body[:4000] + "..."
        embed['description'] = body if body else "No release notes available."
    else:
        embed['title'] = f"{repo_name} {display_version}"
        embed['description'] = "No release notes available."

    embed['url'] = f"https://github.com/{repo_owner}/{repo_name}"

    if 'embed' in config:
        ec = config['embed']
        if ec.get('color'): embed['color'] = ec['color']
        if ec.get('author'):
            embed['author'] = {
                'name': ec['author'].get('name', ''),
                'url': ec['author'].get('url', ''),
                'icon_url': ec['author'].get('icon_url', '')
            }
        if ec.get('thumbnail', {}).get('url'):
            embed['thumbnail'] = {'url': ec['thumbnail']['url']}
        if ec.get('image', {}).get('url'):
            embed['image'] = {'url': ec['image']['url']}
        if ec.get('footer'):
            embed['footer'] = {
                'text': ec['footer'].get('text', ''),
                'icon_url': ec['footer'].get('icon_url', '')
            }
        if ec.get('timestamp', True):
            embed['timestamp'] = datetime.now(timezone.utc).isoformat()
        if 'fields' in ec:
            embed['fields'] = []
            for field in ec['fields']:
                embed['fields'].append({
                    'name': field.get('name', ''),
                    'value': field.get('value', ''),
                    'inline': field.get('inline', False)
                })

    payload['embeds'] = [embed]
    payload_json = json.dumps(payload)

    import urllib.request
    # Cloudflare blocks the default Python-urllib User-Agent with rule 1010.
    # Send a bot-style UA so the webhook actually reaches Discord.
    ua = f'{repo_name}ReleaseBot/1.0 (+https://github.com/{repo_owner}/{repo_name})'

    # If edit.message_id is set, PATCH that message; otherwise POST a new one.
    # Doing both produces a duplicate embed.
    msg_id = (config.get('edit') or {}).get('message_id', '')
    if msg_id:
        url = f"{webhook_url}/messages/{msg_id}"
        method = 'PATCH'
        success_label = f"Discord message {msg_id} updated"
    else:
        url = webhook_url
        method = 'POST'
        success_label = "Discord notification sent"

    req = urllib.request.Request(
        url, data=payload_json.encode('utf-8'),
        headers={'Content-Type': 'application/json', 'User-Agent': ua},
        method=method)

    try:
        resp = urllib.request.urlopen(req)
        print(f"{success_label} (HTTP {resp.status})")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"Discord webhook error: {e.code} {body}")
        sys.exit(1)

if __name__ == '__main__':
    main()
