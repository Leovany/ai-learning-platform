# 测试用 PDF

请上传**含可选中文本层**的 PDF（非纯扫描件）以获得最佳出题效果。

验证 PDF 解析是否正常：

```bash
cd backend && mvn test -Dtest=PdfParseServiceTest
```

该测试会在临时目录生成并解析一份示例 PDF。
