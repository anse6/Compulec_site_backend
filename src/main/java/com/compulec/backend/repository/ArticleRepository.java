package com.compulec.backend.repository;

import com.compulec.backend.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    Page<Article> findByPublishedTrue(Pageable pageable);

    long countByPublishedTrue();

    /** Tableau de bord : nombre de articles créés par mois depuis {@code since} → [année, mois, nombre]. */
    @Query("SELECT YEAR(x.createdAt), MONTH(x.createdAt), COUNT(x) FROM Article x "
            + "WHERE x.createdAt >= :since GROUP BY YEAR(x.createdAt), MONTH(x.createdAt)")
    List<Object[]> countCreatedPerMonthSince(LocalDateTime since);
}
