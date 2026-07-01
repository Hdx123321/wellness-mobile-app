package com.wellnessmate.food.repository;

import com.wellnessmate.food.domain.FoodCatalogItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodCatalogRepository extends JpaRepository<FoodCatalogItem, Long> {
  @Query("""
      select food from FoodCatalogItem food
      where (:query = '' or lower(food.name) like lower(concat('%', :query, '%'))
        or lower(food.searchTerms) like lower(concat('%', :query, '%')))
      and (:categoryId is null or food.categoryId = :categoryId)
      order by food.name
      """)
  List<FoodCatalogItem> search(@Param("query") String query, @Param("categoryId") Long categoryId,
                               Pageable pageable);
}
