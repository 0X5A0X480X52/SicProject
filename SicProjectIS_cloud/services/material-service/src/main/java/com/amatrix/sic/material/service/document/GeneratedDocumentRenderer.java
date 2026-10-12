package com.amatrix.sic.material.service.document;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.amatrix.sic.contracts.DocumentGenerationCommand;

import tools.jackson.databind.ObjectMapper;

/**
 * 把命令和 JSON 快照渲染为确定性的一页 PDF，手工写 PDF 结构；非 ASCII 转问号，无业务模板引擎。
 */
@Component
public class GeneratedDocumentRenderer {
    private final ObjectMapper objectMapper;

    public GeneratedDocumentRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] render(DocumentGenerationCommand command) {
        List<String> lines = new ArrayList<>();
        lines.add("SIC Project Workflow Document");
        lines.add("Document: " + ascii(command.documentTypeName()));
        lines.add("Document type: " + ascii(command.documentTypeCode()));
        lines.add("Template: " + ascii(command.templateCode()));
        lines.add("Project: " + command.projectId());
        lines.add("Module: " + command.moduleInstanceId() + " / " + ascii(command.moduleType()));
        lines.add("State: " + ascii(command.stateCode()) + " / seq " + command.stateSeq());
        lines.add("Command: " + ascii(command.commandId()));
        try {
            lines.addAll(wrap("Snapshot: " + ascii(objectMapper.writeValueAsString(command.snapshot())), 92));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize generated document snapshot", ex);
        }
        return pdf(lines);
    }

    private static byte[] pdf(List<String> lines) {
        StringBuilder stream = new StringBuilder("BT\n/F1 10 Tf\n40 800 Td\n13 TL\n");
        for (String line : lines) stream.append('(').append(escape(line)).append(") Tj\nT*\n");
        stream.append("ET\n");
        byte[] content = stream.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> objects = List.of(
                bytes("<< /Type /Catalog /Pages 2 0 R >>"),
                bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"),
                bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                concat(bytes("<< /Length " + content.length + " >>\nstream\n"), content, bytes("endstream")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, bytes("%PDF-1.4\n%SIC\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size());
            write(output, bytes((i + 1) + " 0 obj\n"));
            write(output, objects.get(i));
            write(output, bytes("\nendobj\n"));
        }
        int xref = output.size();
        write(output, bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
        for (Integer offset : offsets) write(output, bytes(String.format("%010d 00000 n \n", offset)));
        write(output, bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }

    private static List<String> wrap(String value, int length) {
        List<String> lines = new ArrayList<>();
        for (int from = 0; from < value.length(); from += length) {
            lines.add(value.substring(from, Math.min(value.length(), from + length)));
        }
        return lines;
    }

    private static String ascii(Object value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        for (char character : String.valueOf(value).toCharArray()) {
            result.append(character >= 32 && character <= 126 ? character : '?');
        }
        return result.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) write(output, value);
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, byte[] value) {
        output.writeBytes(value);
    }
}
