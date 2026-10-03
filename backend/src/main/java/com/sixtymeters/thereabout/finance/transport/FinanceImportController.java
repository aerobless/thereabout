package com.sixtymeters.thereabout.finance.transport;

import com.sixtymeters.thereabout.access.UserContext;
import com.sixtymeters.thereabout.finance.service.*;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finances/imports")
@RequiredArgsConstructor
public class FinanceImportController {
  private final FinanceImportService imports;
  private final ImportCsvReader csv;
  private final UserContext users;

  @ModelAttribute
  public void noCache(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public GenFinanceImportJob prepare(@RequestParam long accountId, @RequestPart MultipartFile file)
      throws IOException {
    com.sixtymeters.thereabout.finance.domain.FinanceRules.require(
        file.getSize() <= ImportCsvReader.MAX_BYTES, "CSV exceeds 2 MiB");
    return imports.prepare(
        users.current(),
        new GenFinanceImportPrepareInput()
            .accountId(accountId)
            .fileName(file.getOriginalFilename())
            .csvText(csv.decode(file.getBytes())));
  }

  @GetMapping("/{jobId}")
  public GenFinanceImportJob get(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int pageSize) {
    return imports.get(
        users.current(), new GenFinanceImportQuery().jobId(jobId).page(page).pageSize(pageSize));
  }

  @PostMapping("/review")
  public GenFinanceImportJob review(@Valid @RequestBody GenFinanceImportReviewInput input) {
    return imports.review(users.current(), input);
  }

  @PostMapping("/cancel")
  public GenFinanceImportJob cancel(@Valid @RequestBody GenFinanceImportQuery input) {
    return imports.cancel(users.current(), input);
  }

  @PostMapping("/approve")
  public GenFinanceImportApproval approve(@Valid @RequestBody GenFinanceImportApproveInput input) {
    return imports.approve(users.current(), input);
  }
}
