package com.ailearning.platform.document;

import com.ailearning.platform.common.Result;
import com.ailearning.platform.document.dto.DocumentPageVO;
import com.ailearning.platform.document.dto.DocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentPageService documentPageService;

    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(documentService.upload(file));
    }

    @GetMapping
    public Result<List<DocumentVO>> list() {
        return Result.ok(documentService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> get(@PathVariable Long id) {
        return Result.ok(documentService.getById(id));
    }

    @GetMapping("/{id}/pages/{page}")
    public Result<DocumentPageVO> getPage(
            @PathVariable Long id,
            @PathVariable int page,
            @RequestParam(required = false) String highlight) {
        return Result.ok(documentPageService.getPage(id, page, highlight));
    }

    @PostMapping("/{id}/reparse")
    public Result<DocumentVO> reparse(@PathVariable Long id) throws IOException {
        return Result.ok(documentService.reparse(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) throws IOException {
        documentService.delete(id);
        return Result.ok();
    }
}
