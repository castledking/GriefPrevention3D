package me.ryanhamshire.GriefPrevention;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes message files without handing them to a YAML parser.
 * <p>
 * Message values are formatting codes and free text, not YAML documents. Running them through YAML
 * means a value that starts with {@code &} is read as an anchor ({@code &1Now ignoring claims.}
 * silently becomes {@code ignoring claims.}), a {@code #} starts a comment, and re-saving the file
 * strips any quotes the user added to work around that. Here everything after {@code key:} is taken
 * literally, so color codes work with or without quotes, and saving only appends keys that are
 * missing instead of rewriting the file.
 */
final class MessageFile
{

    // key lines: two spaces of indent, an identifier, a colon, then the raw value (if any)
    private static final Pattern ENTRY = Pattern.compile("^([ \\t]*)([A-Za-z0-9_][A-Za-z0-9_.\\-]*):(?:[ \\t](.*))?$");

    private final List<String> lines = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> additions = new LinkedHashMap<>();
    private final Map<String, String> additionNotes = new LinkedHashMap<>();
    private String lineSeparator = System.lineSeparator();

    private MessageFile()
    {
    }

    static @NotNull MessageFile empty()
    {
        return new MessageFile();
    }

    static @NotNull MessageFile load(@NotNull File file)
    {
        MessageFile messageFile = new MessageFile();
        try
        {
            byte[] bytes = Files.readAllBytes(file.toPath());
            messageFile.parse(new String(bytes, StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            GriefPrevention.AddLogEntry("Failed to read " + file.getName() + ": " + e.getMessage(),
                    CustomLogEntryTypes.Debug, false);
        }
        return messageFile;
    }

    static @NotNull MessageFile load(@NotNull InputStream in) throws IOException
    {
        MessageFile messageFile = new MessageFile();
        messageFile.parse(readFully(in));
        return messageFile;
    }

    boolean contains(@NotNull String key)
    {
        return values.containsKey(key);
    }

    @NotNull String getString(@NotNull String key, @NotNull String defaultValue)
    {
        String value = values.get(key);
        return value != null ? value : defaultValue;
    }

    @NotNull Collection<String> keys()
    {
        return new ArrayList<>(values.keySet());
    }

    // queues a key to be appended to the file on the next save
    void set(@NotNull String key, @NotNull String value, @Nullable String notes)
    {
        values.put(key, value);
        additions.put(key, value);
        if (notes != null)
        {
            additionNotes.put(key, notes);
        }
    }

    boolean hasPendingAdditions()
    {
        return !additions.isEmpty();
    }

    /**
     * Appends queued keys to the file, leaving every existing line - including the user's comments,
     * quoting and ordering - untouched.
     */
    void save(@NotNull File file) throws IOException
    {
        if (additions.isEmpty()) return;

        List<String> output = new ArrayList<>(lines);
        for (Map.Entry<String, String> addition : additions.entrySet())
        {
            insert(output, addition.getKey(), addition.getValue(), additionNotes.get(addition.getKey()));
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }

        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))
        {
            for (int i = 0; i < output.size(); i++)
            {
                if (i > 0) writer.write(lineSeparator);
                writer.write(output.get(i));
            }
            writer.write(lineSeparator);
        }

        lines.clear();
        lines.addAll(output);
        additions.clear();
        additionNotes.clear();
    }

    private void insert(@NotNull List<String> output, @NotNull String key, @NotNull String value, @Nullable String notes)
    {
        int lastDot = key.lastIndexOf('.');
        String parentPath = lastDot < 0 ? null : key.substring(0, lastDot);
        String leaf = lastDot < 0 ? key : key.substring(lastDot + 1);

        int indent = 0;
        int insertAt = output.size();

        if (parentPath != null)
        {
            List<Entry> entries = scan(output);
            Entry section = null;
            for (Entry entry : entries)
            {
                if (entry.section && entry.path.equals(parentPath))
                {
                    section = entry;
                    break;
                }
            }

            if (section == null)
            {
                // the section this key belongs to is missing entirely - create it at the end of the file
                if (!output.isEmpty() && !output.get(output.size() - 1).trim().isEmpty())
                {
                    output.add("");
                }
                String[] segments = parentPath.split("\\.");
                for (int depth = 0; depth < segments.length; depth++)
                {
                    output.add(spaces(depth * 2) + segments[depth] + ":");
                }
                indent = segments.length * 2;
                insertAt = output.size();
            }
            else
            {
                indent = section.indent + 2;
                insertAt = section.lineIndex + 1;
                for (Entry entry : entries)
                {
                    if (entry.lineIndex > section.lineIndex && entry.indent > section.indent)
                    {
                        insertAt = entry.lineIndex + 1;
                        if (entry.indent < indent) indent = entry.indent;
                    }
                    else if (entry.lineIndex > section.lineIndex && entry.indent <= section.indent)
                    {
                        break;
                    }
                }
            }
        }

        List<String> added = new ArrayList<>();
        if (notes != null && !notes.trim().isEmpty())
        {
            added.add(spaces(indent) + "# " + notes.trim());
        }
        added.add(spaces(indent) + leaf + ": " + quote(value));
        output.addAll(insertAt, added);
    }

    private void parse(@NotNull String contents)
    {
        if (contents.contains("\r\n"))
        {
            lineSeparator = "\r\n";
        }

        String body = contents;
        // a trailing newline just ends the last line, it isn't an empty line of its own
        if (body.endsWith("\r\n")) body = body.substring(0, body.length() - 2);
        else if (body.endsWith("\n") || body.endsWith("\r")) body = body.substring(0, body.length() - 1);

        for (String line : body.split("\r\n|\r|\n", -1))
        {
            lines.add(line);
        }

        for (Entry entry : scan(lines))
        {
            if (!entry.section)
            {
                values.put(entry.path, unquote(entry.value));
            }
        }
    }

    // walks the file's key lines, resolving each to a dotted path based on indentation
    private static @NotNull List<Entry> scan(@NotNull List<String> lines)
    {
        List<Entry> entries = new ArrayList<>();
        List<String> pathKeys = new ArrayList<>();
        List<Integer> pathIndents = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++)
        {
            String line = stripTrailingWhitespace(lines.get(i));
            if (line.isEmpty() || line.trim().startsWith("#")) continue;

            Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) continue;

            int indent = matcher.group(1).length();
            String key = matcher.group(2);
            String value = matcher.group(3);

            while (!pathIndents.isEmpty() && pathIndents.get(pathIndents.size() - 1) >= indent)
            {
                pathIndents.remove(pathIndents.size() - 1);
                pathKeys.remove(pathKeys.size() - 1);
            }

            StringBuilder path = new StringBuilder();
            for (String parent : pathKeys)
            {
                path.append(parent).append('.');
            }
            path.append(key);

            if (value == null || value.trim().isEmpty())
            {
                // no value on this line, so it opens a section
                entries.add(new Entry(i, indent, path.toString(), true, ""));
                pathKeys.add(key);
                pathIndents.add(indent);
            }
            else
            {
                entries.add(new Entry(i, indent, path.toString(), false, value));
            }
        }

        return entries;
    }

    /**
     * Strips quotes if the value is wrapped in them, so files written for a YAML parser keep working.
     * Everything else is taken literally - a lone {@code &}, {@code #} or backslash is just text.
     */
    private static @NotNull String unquote(@NotNull String value)
    {
        String trimmed = stripTrailingWhitespace(value);
        if (trimmed.length() < 2) return trimmed;

        char quote = trimmed.charAt(0);
        if (quote != '\'' && quote != '"') return trimmed;
        if (trimmed.charAt(trimmed.length() - 1) != quote) return trimmed;

        String inner = trimmed.substring(1, trimmed.length() - 1);
        StringBuilder result = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++)
        {
            char current = inner.charAt(i);
            if (quote == '\'' && current == '\'' && i + 1 < inner.length() && inner.charAt(i + 1) == '\'')
            {
                result.append('\'');
                i++;
            }
            else if (quote == '"' && current == '\\' && i + 1 < inner.length()
                    && (inner.charAt(i + 1) == '"' || inner.charAt(i + 1) == '\\'))
            {
                result.append(inner.charAt(i + 1));
                i++;
            }
            else
            {
                result.append(current);
            }
        }
        return result.toString();
    }

    // written quoted so the files stay valid YAML for anything else that reads them
    private static @NotNull String quote(@NotNull String value)
    {
        return "'" + value.replace("\r\n", "\\n").replace("\n", "\\n").replace("'", "''") + "'";
    }

    private static @NotNull String spaces(int count)
    {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++)
        {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static @NotNull String stripTrailingWhitespace(@NotNull String value)
    {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t'))
        {
            end--;
        }
        return value.substring(0, end);
    }

    private static @NotNull String readFully(@NotNull InputStream in) throws IOException
    {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1)
        {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class Entry
    {
        private final int lineIndex;
        private final int indent;
        private final String path;
        private final boolean section;
        private final String value;

        private Entry(int lineIndex, int indent, @NotNull String path, boolean section, @NotNull String value)
        {
            this.lineIndex = lineIndex;
            this.indent = indent;
            this.path = path;
            this.section = section;
            this.value = value;
        }
    }

}
