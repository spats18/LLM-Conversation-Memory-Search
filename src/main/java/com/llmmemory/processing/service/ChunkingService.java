package com.llmmemory.processing.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChunkingService {
    private final DocumentSplitter splitter;

    ChunkingService(
            @Value("${app.chunking.size}") int chunkSize,
            @Value("${app.chunking.overlap}") int chunkOverlap) {
        this.splitter = new DocumentByCharacterSplitter(chunkSize, chunkOverlap);
    }

    public List<String> chunkText(String rawContent) {
        Document document = Document.from(rawContent);
        List<TextSegment> segments = splitter.split(document);
        List<String> chunks = segments.stream()
                .map(TextSegment::text)
                .toList();
        log.debug("Split {} chars into {} chunks", rawContent.length(), chunks.size());
        return chunks;
    }
}
