package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Adds missing block-style YAML mapping entries without re-serializing user content. */
final class YamlDefaultsUpdater
{
    private YamlDefaultsUpdater()
    {
    }

    static @NotNull String mergeMissing(@NotNull String existing, @NotNull String defaults)
            throws IOException
    {
        if (existing.trim().isEmpty())
        {
            return defaults;
        }

        Document existingDocument = Document.parse(existing);
        Document defaultDocument = Document.parse(defaults);
        Map<Integer, List<String>> additions = new TreeMap<>();
        collectAdditions(
                existingDocument.root,
                defaultDocument.root,
                defaultDocument,
                additions
        );
        if (additions.isEmpty())
        {
            return existing;
        }

        List<String> mergedLines = new ArrayList<>(existingDocument.lines);
        List<Integer> insertionPoints = new ArrayList<>(additions.keySet());
        insertionPoints.sort(Collections.reverseOrder());
        for (int insertionPoint : insertionPoints)
        {
            mergedLines.addAll(insertionPoint, additions.get(insertionPoint));
        }
        return existingDocument.join(mergedLines);
    }

    private static void collectAdditions(
            @NotNull Node existingParent,
            @NotNull Node defaultParent,
            @NotNull Document defaultDocument,
            @NotNull Map<Integer, List<String>> additions)
    {
        int childIndent = childIndent(existingParent, defaultParent);
        List<String> missingChildren = new ArrayList<>();
        for (Node defaultChild : defaultParent.children.values())
        {
            Node existingChild = existingParent.children.get(defaultChild.key);
            if (existingChild == null)
            {
                missingChildren.addAll(defaultDocument.render(defaultChild, childIndent));
                continue;
            }

            if (!defaultChild.children.isEmpty() && existingChild.acceptsBlockChildren)
            {
                collectAdditions(existingChild, defaultChild, defaultDocument, additions);
            }
        }

        if (!missingChildren.isEmpty())
        {
            additions.computeIfAbsent(existingParent.endLine, ignored -> new ArrayList<>())
                    .addAll(missingChildren);
        }
    }

    private static int childIndent(@NotNull Node existingParent, @NotNull Node defaultParent)
    {
        if (!existingParent.children.isEmpty())
        {
            return existingParent.children.values().iterator().next().indent;
        }
        if (!defaultParent.children.isEmpty())
        {
            Node firstDefaultChild = defaultParent.children.values().iterator().next();
            if (existingParent.root)
            {
                return firstDefaultChild.indent;
            }
            return existingParent.indent + firstDefaultChild.indent - defaultParent.indent;
        }
        return existingParent.root ? 0 : existingParent.indent + 2;
    }

    private static final class Document
    {
        private final List<String> lines;
        private final String lineSeparator;
        private final boolean terminalLineSeparator;
        private final Node root;

        private Document(
                @NotNull List<String> lines,
                @NotNull String lineSeparator,
                boolean terminalLineSeparator,
                @NotNull Node root)
        {
            this.lines = lines;
            this.lineSeparator = lineSeparator;
            this.terminalLineSeparator = terminalLineSeparator;
            this.root = root;
        }

        private static @NotNull Document parse(@NotNull String contents) throws IOException
        {
            String lineSeparator = detectLineSeparator(contents);
            boolean terminalLineSeparator = endsWithLineSeparator(contents);
            String[] split = contents.split("\\r\\n|\\n|\\r", -1);
            int lineCount = split.length;
            if (terminalLineSeparator)
            {
                lineCount--;
            }
            List<String> lines = new ArrayList<>(lineCount);
            for (int index = 0; index < lineCount; index++)
            {
                lines.add(split[index]);
            }

            Node root = Node.root(lines.size());
            Deque<Node> parents = new ArrayDeque<>();
            parents.push(root);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++)
            {
                String line = lines.get(lineIndex);
                int indent = indentation(line, lineIndex);
                String trimmed = line.substring(Math.min(indent, line.length())).trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#"))
                {
                    continue;
                }

                while (!parents.peek().root && indent <= parents.peek().indent)
                {
                    parents.pop().endLine = lineIndex;
                }
                if (!parents.peek().root && !parents.peek().acceptsBlockChildren)
                {
                    continue;
                }

                ParsedMapping mapping = parseMapping(line, indent);
                if (mapping == null)
                {
                    continue;
                }

                Node node = new Node(
                        mapping.key,
                        indent,
                        lineIndex,
                        lines.size(),
                        mapping.acceptsBlockChildren,
                        false
                );
                parents.peek().children.putIfAbsent(mapping.key, node);
                parents.push(node);
            }
            while (!parents.peek().root)
            {
                parents.pop().endLine = lines.size();
            }
            return new Document(lines, lineSeparator, terminalLineSeparator, root);
        }

        private @NotNull List<String> render(@NotNull Node node, int targetIndent)
        {
            List<String> rendered = new ArrayList<>(node.endLine - node.startLine);
            for (int lineIndex = node.startLine; lineIndex < node.endLine; lineIndex++)
            {
                String line = this.lines.get(lineIndex);
                if (line.isBlank())
                {
                    rendered.add(line);
                    continue;
                }

                int sourceIndent = leadingSpaces(line);
                int relativeIndent = Math.max(0, sourceIndent - node.indent);
                rendered.add(" ".repeat(targetIndent + relativeIndent) + line.substring(sourceIndent));
            }
            return rendered;
        }

        private @NotNull String join(@NotNull List<String> mergedLines)
        {
            String merged = String.join(this.lineSeparator, mergedLines);
            if (this.terminalLineSeparator)
            {
                merged += this.lineSeparator;
            }
            return merged;
        }

        private static @NotNull String detectLineSeparator(@NotNull String contents)
        {
            for (int index = 0; index < contents.length(); index++)
            {
                char character = contents.charAt(index);
                if (character == '\r')
                {
                    return index + 1 < contents.length() && contents.charAt(index + 1) == '\n'
                            ? "\r\n"
                            : "\r";
                }
                if (character == '\n')
                {
                    return "\n";
                }
            }
            return "\n";
        }

        private static boolean endsWithLineSeparator(@NotNull String contents)
        {
            return !contents.isEmpty()
                    && (contents.charAt(contents.length() - 1) == '\n'
                    || contents.charAt(contents.length() - 1) == '\r');
        }
    }

    private static final class Node
    {
        private final String key;
        private final int indent;
        private final int startLine;
        private int endLine;
        private final boolean acceptsBlockChildren;
        private final boolean root;
        private final Map<String, Node> children = new LinkedHashMap<>();

        private Node(
                @NotNull String key,
                int indent,
                int startLine,
                int endLine,
                boolean acceptsBlockChildren,
                boolean root)
        {
            this.key = key;
            this.indent = indent;
            this.startLine = startLine;
            this.endLine = endLine;
            this.acceptsBlockChildren = acceptsBlockChildren;
            this.root = root;
        }

        private static @NotNull Node root(int endLine)
        {
            return new Node("", -1, 0, endLine, true, true);
        }
    }

    private static final class ParsedMapping
    {
        private final String key;
        private final boolean acceptsBlockChildren;

        private ParsedMapping(@NotNull String key, boolean acceptsBlockChildren)
        {
            this.key = key;
            this.acceptsBlockChildren = acceptsBlockChildren;
        }
    }

    private static int indentation(@NotNull String line, int lineIndex) throws IOException
    {
        int spaces = leadingSpaces(line);
        if (spaces < line.length() && line.charAt(spaces) == '\t')
        {
            throw new IOException("Cannot update YAML containing tab indentation at line " + (lineIndex + 1) + ".");
        }
        return spaces;
    }

    private static int leadingSpaces(@NotNull String line)
    {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ')
        {
            spaces++;
        }
        return spaces;
    }

    private static ParsedMapping parseMapping(@NotNull String line, int indent)
    {
        String content = line.substring(indent);
        if (content.startsWith("-")
                || content.startsWith("?")
                || content.startsWith("%")
                || content.equals("---")
                || content.equals("..."))
        {
            return null;
        }

        int colon = mappingColon(content);
        if (colon < 0)
        {
            return null;
        }
        String key = unquote(content.substring(0, colon).trim());
        if (key.isEmpty())
        {
            return null;
        }

        String value = content.substring(colon + 1).trim();
        boolean acceptsBlockChildren = value.isEmpty() || value.startsWith("#");
        return new ParsedMapping(key, acceptsBlockChildren);
    }

    private static int mappingColon(@NotNull String content)
    {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < content.length(); index++)
        {
            char character = content.charAt(index);
            if (doubleQuoted && character == '\\')
            {
                index++;
                continue;
            }
            if (!doubleQuoted && character == '\'')
            {
                if (singleQuoted && index + 1 < content.length() && content.charAt(index + 1) == '\'')
                {
                    index++;
                }
                else
                {
                    singleQuoted = !singleQuoted;
                }
                continue;
            }
            if (!singleQuoted && character == '"')
            {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && character == ':')
            {
                if (index + 1 == content.length() || Character.isWhitespace(content.charAt(index + 1)))
                {
                    return index;
                }
            }
        }
        return -1;
    }

    private static @NotNull String unquote(@NotNull String key)
    {
        if (key.length() < 2)
        {
            return key;
        }
        if (key.startsWith("'") && key.endsWith("'"))
        {
            return key.substring(1, key.length() - 1).replace("''", "'");
        }
        if (key.startsWith("\"") && key.endsWith("\""))
        {
            return key.substring(1, key.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return key;
    }
}
