package net.coboogie.recommendation.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 카드에 사용할 IAB 상위/하위 카테고리를 선택한다.
 */
@Component
public class RecommendationCategorySelector {

    private static final List<String> FALLBACK_MAIN_CATEGORIES = List.of(
            "음식",
            "문화/예술",
            "엔터테인먼트",
            "라이프스타일",
            "여행",
            "스포츠/피트니스",
            "패션/뷰티",
            "테크"
    );

    /**
     * 초기 뽑기에 사용할 서로 다른 상위 카테고리 3개를 선택한다.
     *
     * @param profile 추천 프로필
     * @return 상위 카테고리 3개
     */
    public List<String> selectInitialMainCategories(RecommendationProfile profile) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        addProfileCategories(selected, profile.mainCategoryFreq());
        addFallbackCategories(selected);
        return selected.stream().limit(3).toList();
    }

    /**
     * shuffle에서 새 카드 1장에 사용할 상위 카테고리를 선택한다.
     *
     * @param profile            추천 프로필
     * @param excludedCategories 현재 남은 카드와 직전에 본 카드의 상위 카테고리
     * @return 새 카드 상위 카테고리
     */
    public String selectReplacementMainCategory(RecommendationProfile profile, Set<String> excludedCategories) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(profile.mainCategoryFreq().keySet());
        candidates.addAll(FALLBACK_MAIN_CATEGORIES);

        for (String candidate : candidates) {
            if (!excludedCategories.contains(candidate)) {
                return candidate;
            }
        }

        Set<String> activeOnly = new LinkedHashSet<>(excludedCategories);
        if (!activeOnly.isEmpty()) {
            activeOnly.remove(activeOnly.iterator().next());
        }
        for (String candidate : candidates) {
            if (!activeOnly.contains(candidate)) {
                return candidate;
            }
        }
        return FALLBACK_MAIN_CATEGORIES.get(0);
    }

    /**
     * 상위 카테고리에 맞는 대표 하위 카테고리를 선택한다.
     *
     * @param profile      추천 프로필
     * @param mainCategory 상위 카테고리
     * @return 하위 카테고리
     */
    public String selectSubCategory(RecommendationProfile profile, String mainCategory) {
        for (Map.Entry<String, Integer> entry : profile.subCategoryFreq().entrySet()) {
            String category = entry.getKey();
            if (category.startsWith(mainCategory + ">")) {
                int delimiter = category.indexOf('>');
                return delimiter > 0 ? category.substring(delimiter + 1).trim() : null;
            }
        }
        return null;
    }

    private void addProfileCategories(LinkedHashSet<String> selected, Map<String, Integer> mainCategoryFreq) {
        for (String category : mainCategoryFreq.keySet()) {
            if (category != null && !category.isBlank()) {
                selected.add(category);
            }
        }
    }

    private void addFallbackCategories(LinkedHashSet<String> selected) {
        selected.addAll(FALLBACK_MAIN_CATEGORIES);
    }
}
