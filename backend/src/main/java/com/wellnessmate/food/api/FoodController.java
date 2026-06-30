package com.wellnessmate.food.api;

import com.wellnessmate.food.service.FoodImageAnalyzer;
import com.wellnessmate.food.service.FoodService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/food")
public class FoodController {
  private final FoodService food;
  private final FoodImageAnalyzer analyzer;

  public FoodController(FoodService food, FoodImageAnalyzer analyzer) {
    this.food = food;
    this.analyzer = analyzer;
  }

  @GetMapping("/catalog")
  public List<FoodCatalogResponse> catalog(@RequestParam(defaultValue = "") String query,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "50") int limit) {
    return food.search(query, categoryId, limit);
  }

  @GetMapping("/categories")
  public List<FoodCategoryResponse> categories() {
    return food.listCategories();
  }

  @GetMapping("/catalog/{id}")
  public FoodDetailResponse foodDetail(@PathVariable Long id) {
    return food.foodDetail(id);
  }

  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  public FoodEntryResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody FoodEntryRequest request) {
    return food.createFromCatalog(userId(jwt), request);
  }

  @PostMapping("/entries/analyzed")
  @ResponseStatus(HttpStatus.CREATED)
  public FoodEntryResponse createAnalyzed(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody AnalyzedFoodEntryRequest request) {
    return food.createFromAnalysis(userId(jwt), request);
  }

  @GetMapping("/entries")
  public List<FoodEntryResponse> entries(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return food.list(userId(jwt), from, to);
  }

  @DeleteMapping("/entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt,
                     @org.springframework.web.bind.annotation.PathVariable Long id) {
    food.delete(userId(jwt), id);
  }

  @PostMapping(value = "/analyze", consumes = "multipart/form-data")
  public FoodAnalysisResponse analyze(@RequestParam("image") MultipartFile image) throws IOException {
    return analyzer.analyze(image.getBytes(), image.getContentType());
  }

  private Long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
