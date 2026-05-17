package com.ailearning.platform.document;

import com.ailearning.platform.common.BusinessException;
import com.ailearning.platform.config.AppProperties;
import com.ailearning.platform.document.dto.DocumentVO;
import com.ailearning.platform.quiz.QuizService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final PdfParseService pdfParseService;
    private final QuizService quizService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DocumentVO upload(MultipartFile file) throws IOException {
        validatePdfFile(file);

        String originalName = Path.of(file.getOriginalFilename()).getFileName().toString();
        String storedName = UUID.randomUUID() + ".pdf";
        Path targetPath = Path.of(appProperties.getUploadDir(), storedName);

        Files.copy(file.getInputStream(), targetPath);

        LearningDocument doc = new LearningDocument();
        doc.setFileName(originalName);
        doc.setFilePath(targetPath.toString());
        doc.setFileSize(file.getSize());
        doc.setStatus(DocumentStatus.PENDING);
        doc = documentRepository.save(doc);

        try {
            PdfParseService.PdfParseResult result = pdfParseService.parse(targetPath);
            doc.setExtractedText(result.text());
            doc.setPageCount(result.pageCount());
            doc.setPageTextsJson(DocumentPageService.toJson(result.pages(), objectMapper));
            doc.setStatus(DocumentStatus.PARSED);
            if (result.text().isBlank()) {
                log.warn("PDF parsed but no text extracted: {}", originalName);
            }
        } catch (Exception e) {
            log.error("Failed to parse PDF: {}", originalName, e);
            doc.setStatus(DocumentStatus.FAILED);
        }

        doc = documentRepository.save(doc);
        return DocumentVO.detail(doc);
    }

    @Transactional(readOnly = true)
    public List<DocumentVO> listAll() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentVO::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentVO getById(Long id) {
        return DocumentVO.detail(findOrThrow(id));
    }

    /** 重新解析 PDF 并更新分页文本（用于补全页码信息） */
    @Transactional
    public DocumentVO reparse(Long id) throws IOException {
        LearningDocument doc = findOrThrow(id);
        Path filePath = Path.of(doc.getFilePath());
        if (!Files.exists(filePath)) {
            throw BusinessException.notFound("PDF 文件不存在");
        }
        try {
            PdfParseService.PdfParseResult result = pdfParseService.parse(filePath);
            doc.setExtractedText(result.text());
            doc.setPageCount(result.pageCount());
            doc.setPageTextsJson(DocumentPageService.toJson(result.pages(), objectMapper));
            doc.setStatus(DocumentStatus.PARSED);
        } catch (Exception e) {
            log.error("Reparse PDF failed: {}", doc.getFileName(), e);
            doc.setStatus(DocumentStatus.FAILED);
            throw BusinessException.badRequest("重新解析失败: " + e.getMessage());
        }
        doc = documentRepository.save(doc);
        return DocumentVO.detail(doc);
    }

    @Transactional
    public void delete(Long id) throws IOException {
        LearningDocument doc = findOrThrow(id);
        quizService.deleteByDocumentId(id);
        Path filePath = Path.of(doc.getFilePath());
        Files.deleteIfExists(filePath);
        documentRepository.delete(doc);
    }

    private LearningDocument findOrThrow(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("文档不存在"));
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择要上传的 PDF 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("文件大小不能超过 20MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 格式文件");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/pdf")
                && !contentType.equals("application/octet-stream")) {
            throw BusinessException.badRequest("文件类型无效，请上传 PDF");
        }
    }
}
