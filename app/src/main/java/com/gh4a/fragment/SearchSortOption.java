package com.gh4a.fragment;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.1.20 D-06: 搜索排序选项表（Strategy 式枚举）——此前同一映射写了三遍：
 * SORT_LOOKUP（菜单 id → 排序字段/方向）、sortTitleRes() switch（菜单 id →
 * 标题资源）、菜单 XML。现收敛为一张表：菜单 id、排序字段、方向、标题资源、
 * 适用搜索类型各只写一次；排序表按枚举声明顺序出（与原先三张分类型
 * 数组的顺序一致），行为不变。
 */
public enum SearchSortOption {
    BEST_MATCH(R.id.search_sort_best_match, null, null,
            R.string.search_sort_best_match, 7 /* TYPE_ALL */),

    REPO_STARS_DESC(R.id.search_sort_repo_stars_desc, "stars", "desc",
            R.string.search_sort_repo_stars_desc, 1 /* TYPE_REPO */),
    REPO_STARS_ASC(R.id.search_sort_repo_stars_asc, "stars", "asc",
            R.string.search_sort_repo_stars_asc, 1 /* TYPE_REPO */),
    REPO_FORKS_DESC(R.id.search_sort_repo_forks_desc, "forks", "desc",
            R.string.search_sort_repo_forks_desc, 1 /* TYPE_REPO */),
    REPO_FORKS_ASC(R.id.search_sort_repo_forks_asc, "forks", "asc",
            R.string.search_sort_repo_forks_asc, 1 /* TYPE_REPO */),
    REPO_UPDATED_DESC(R.id.search_sort_repo_updated_desc, "updated", "desc",
            R.string.search_sort_repo_updated_desc, 1 /* TYPE_REPO */),
    REPO_UPDATED_ASC(R.id.search_sort_repo_updated_asc, "updated", "asc",
            R.string.search_sort_repo_updated_asc, 1 /* TYPE_REPO */),

    USER_FOLLOWERS_DESC(R.id.search_sort_user_followers_desc, "followers", "desc",
            R.string.search_sort_user_followers_desc, 2 /* TYPE_USER */),
    USER_FOLLOWERS_ASC(R.id.search_sort_user_followers_asc, "followers", "asc",
            R.string.search_sort_user_followers_asc, 2 /* TYPE_USER */),
    USER_REPOSITORIES_DESC(R.id.search_sort_user_repositories_desc, "repositories", "desc",
            R.string.search_sort_user_repositories_desc, 2 /* TYPE_USER */),
    USER_REPOSITORIES_ASC(R.id.search_sort_user_repositories_asc, "repositories", "asc",
            R.string.search_sort_user_repositories_asc, 2 /* TYPE_USER */),
    USER_JOINED_DESC(R.id.search_sort_user_joined_desc, "joined", "desc",
            R.string.search_sort_user_joined_desc, 2 /* TYPE_USER */),
    USER_JOINED_ASC(R.id.search_sort_user_joined_asc, "joined", "asc",
            R.string.search_sort_user_joined_asc, 2 /* TYPE_USER */),

    CODE_INDEXED_DESC(R.id.search_sort_code_indexed_desc, "indexed", "desc",
            R.string.search_sort_code_indexed_desc, 4 /* TYPE_CODE */),
    CODE_INDEXED_ASC(R.id.search_sort_code_indexed_asc, "indexed", "asc",
            R.string.search_sort_code_indexed_asc, 4 /* TYPE_CODE */);

    public static final int TYPE_REPO = 1;
    public static final int TYPE_USER = 2;
    public static final int TYPE_CODE = 4;
    public static final int TYPE_ALL = TYPE_REPO | TYPE_USER | TYPE_CODE;

    public final int menuId;
    public final String sort;
    public final String order;
    public final int titleRes;
    private final int mApplicableTypes;

    SearchSortOption(int menuId, String sort, String order, int titleRes, int applicableTypes) {
        this.menuId = menuId;
        this.sort = sort;
        this.order = order;
        this.titleRes = titleRes;
        mApplicableTypes = applicableTypes;
    }

    /** 该选项是否适用于给定搜索类型（SearchFragment.SEARCH_TYPE_*）。 */
    public boolean appliesTo(int searchType) {
        int flag;
        switch (searchType) {
            case SearchFragment.SEARCH_TYPE_USER: flag = TYPE_USER; break;
            case SearchFragment.SEARCH_TYPE_CODE: flag = TYPE_CODE; break;
            default: flag = TYPE_REPO; break;
        }
        return (mApplicableTypes & flag) != 0;
    }

    /** 按声明顺序返回适用于该搜索类型的选项（排序表顺序与原先一致）。 */
    public static List<SearchSortOption> forSearchType(int searchType) {
        List<SearchSortOption> out = new ArrayList<>();
        for (SearchSortOption option : values()) {
            if (option.appliesTo(searchType)) {
                out.add(option);
            }
        }
        return out;
    }

    public static SearchSortOption fromMenuId(int menuId) {
        for (SearchSortOption option : values()) {
            if (option.menuId == menuId) {
                return option;
            }
        }
        return null;
    }

    /** 按持久化的 (sort, order) 反查菜单 id 对应的选项（无匹配返回 null）。 */
    public static SearchSortOption fromSortOrder(String sort, String order) {
        for (SearchSortOption option : values()) {
            if (java.util.Objects.equals(option.sort, sort)
                    && java.util.Objects.equals(option.order, order)) {
                return option;
            }
        }
        return null;
    }
}
