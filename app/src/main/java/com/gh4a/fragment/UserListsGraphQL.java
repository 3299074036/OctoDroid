package com.gh4a.fragment;


import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Single;

/**
 * GitHub 官方“列表”（Lists，网页 / 官方 App 的“我的列表”）的 GraphQL 访问层。
 *
 * 注意：官方列表没有 REST 接口，app 用的 githubsdk 里也没有对应服务，
 * 只能走 GraphQL。实现照抄同目录的 RadarGraphQL：OkHttp 直调
 * https://api.github.com/graphql，Bearer token 来自当前登录账号。
 * 读和写列表都需要 OAuth 的 user scope，本 app 登录时已申请
 * （LoginModeChooserFragment.SCOPES 含 user），一般无需重新登录；
 * 若账号 scope 不足，服务端会返回权限错误，调用方按普通失败处理并回退。
 *
 * 列表内仓库的解析做了容错：UserList.items 的节点是联合类型
 * UserListItems，其唯一可能形态就是 Repository 本身（官方 schema），
 * 所以查询里必须用 ... on Repository 内联片段直接取仓库字段，
 * 不能假设节点里还包一层 repository（0.0.59 就是错在这里，
 * GraphQL 校验失败导致整个列表拉取失败）。解析时仍递归收集所有带
 * nameWithOwner 的对象，不依赖 items 节点的精确字段名。
 */
public class UserListsGraphQL {

    public static class Repo {
        public final String fullName;
        public final String description;

        Repo(String fullName, String description) {
            this.fullName = fullName;
            this.description = description;
        }
    }

    public static class UserList {
        public final String id;
        public final String name;
        public final String description;
        public final List<Repo> repos;

        UserList(String id, String name, String description, List<Repo> repos) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.repos = repos;
        }
    }

    /** True if the failure is just our own cancellation (do not turn into partial data). */
    private static boolean isCancelled(Exception e) {
        return e instanceof java.io.InterruptedIOException
                || "Canceled".equals(e.getMessage())
                || Thread.currentThread().isInterrupted();
    }

    private static String execute(String query, JSONObject variables) throws Exception {
        // 0.1.20 D-02: 请求执行收敛到 GraphQLClient（本方法仅作委托，
        // 保持 fetchLists 等内部调用点不变；F-11 的取消由 GraphQLClient
        // 的 sActiveCall + 下方 doOnDispose 承载）。
        return GraphQLClient.execute(query, variables);
    }

    /**
     * 0.1.3：官方列表一次查询取全量。骨架和各组仓库嵌套在同一个查询里，
     * 不再先查骨架、再逐个列表串行查 items（7 个分组就是 8 次往返，
     * 分组页要等全部跑完才上屏）。关键仍是 items 节点的联合类型
     * UserListItems，唯一形态就是 Repository，必须用 ... on Repository
     * 内联片段直接取字段；0.0.59 写成 nodes { repository { ... } } 是错的，
     * Repository 没有 repository 字段。单个列表的 items 缺失只影响该列表
     * （视为空列表），不拖垮全部列表；整个查询失败则由调用方回退本机分组。
     *
     * BUG-6：旧查询无分页（lists first:50、items first:100），超量静默
     * 截断。现用游标翻页直到 hasNextPage=false：列表层按 after 翻页；
     * 单个列表的 items 首屏已嵌在列表页查询里，首屏后仍有下一页时再
     * 用 node(id) 查询（UserList 是 Node）按 after 续取该列表的剩余
     * items。常见规模（≤50 组、每组 ≤100 仓）仍只需一次往返。
     */
    private static final String LISTS_PAGE_QUERY =
            "query($login: String!, $after: String) {"
            + "  user(login: $login) {"
            + "    lists(first: 50, after: $after) {"
            + "      nodes {"
            + "        id"
            + "        name"
            + "        description"
            + "        items(first: 100) {"
            + "          nodes {"
            + "            ... on Repository {"
            + "              nameWithOwner"
            + "              description"
            + "            }"
            + "          }"
            + "          pageInfo { hasNextPage endCursor }"
            + "        }"
            + "      }"
            + "      pageInfo { hasNextPage endCursor }"
            + "    }"
            + "  }"
            + "}";

    private static final String LIST_ITEMS_PAGE_QUERY =
            "query($id: ID!, $after: String) {"
            + "  node(id: $id) {"
            + "    ... on UserList {"
            + "      items(first: 100, after: $after) {"
            + "        nodes {"
            + "          ... on Repository {"
            + "            nameWithOwner"
            + "            description"
            + "          }"
            + "        }"
            + "        pageInfo { hasNextPage endCursor }"
            + "      }"
            + "    }"
            + "  }"
            + "}";

    /** 连接的 pageInfo：hasNextPage 与下一页游标（缺失时视为无下一页）。 */
    private static class PageInfo {
        final boolean hasNextPage;
        final String endCursor;

        PageInfo(JSONObject connection) {
            JSONObject info = connection != null ? connection.optJSONObject("pageInfo") : null;
            hasNextPage = info != null && info.optBoolean("hasNextPage", false);
            endCursor = info != null ? info.optString("endCursor", null) : null;
        }
    }

    /** 把 items 连接的 nodes 收集进 byName（按仓库全名去重，保序）。 */
    private static void collectRepos(JSONObject items, Map<String, Repo> byName) {
        JSONArray itemNodes = items != null ? items.optJSONArray("nodes") : null;
        if (itemNodes == null) {
            return;
        }
        for (int j = 0; j < itemNodes.length(); j++) {
            JSONObject repoNode = itemNodes.optJSONObject(j);
            if (repoNode == null) {
                continue;
            }
            String fullName = repoNode.optString("nameWithOwner", "");
            if (!fullName.isEmpty() && !byName.containsKey(fullName)) {
                byName.put(fullName, new Repo(fullName,
                        repoNode.optString("description", "")));
            }
        }
    }

    public static Single<List<UserList>> fetchLists(String login) {
        return Single.fromCallable(() -> {
            List<UserList> lists = new ArrayList<>();
            String listsCursor = null;
            // 列表层翻页：直到 hasNextPage=false。
            do {
                JSONObject vars = new JSONObject();
                vars.put("login", login);
                if (listsCursor != null) {
                    vars.put("after", listsCursor);
                }
                // 0.1.20 F-11: 列表页中途失败不再全有全无——已取到的页
                // 直接返回为部分列表；首屏即失败仍抛错（调用方走失败回退）。
                String json;
                try {
                    json = execute(LISTS_PAGE_QUERY, vars);
                } catch (Exception e) {
                    if (isCancelled(e) || lists.isEmpty()) {
                        throw e;
                    }
                    break;
                }
                JSONObject root = new JSONObject(json);
                JSONObject data = root.optJSONObject("data");
                JSONObject user = data != null ? data.optJSONObject("user") : null;
                JSONObject listsConn = user != null ? user.optJSONObject("lists") : null;
                if (listsConn == null) {
                    break;
                }
                JSONArray nodes = listsConn.optJSONArray("nodes");
                if (nodes != null) {
                    for (int i = 0; i < nodes.length(); i++) {
                        JSONObject node = nodes.optJSONObject(i);
                        if (node == null) {
                            continue;
                        }
                        String id = node.optString("id", "");
                        String name = node.optString("name", "");
                        if (id.isEmpty() || name.isEmpty()) {
                            continue;
                        }
                        // 该列表 items 缺失/为空只影响这一个列表，不拖垮全部列表。
                        Map<String, Repo> byName = new LinkedHashMap<>();
                        JSONObject items = node.optJSONObject("items");
                        collectRepos(items, byName);
                        // items 层翻页：首屏后仍有下一页时，用 node(id) 续取。
                        PageInfo itemsPage = new PageInfo(items);
                        String itemsCursor = itemsPage.endCursor;
                        while (itemsPage.hasNextPage && itemsCursor != null) {
                            JSONObject itemVars = new JSONObject();
                            itemVars.put("id", id);
                            itemVars.put("after", itemsCursor);
                            // 0.1.20 F-11: 某列表续页失败只截断该列表（保留已取仓库），
                            // 不拖垮其余列表；取消则照常抛错。
                            String itemJson;
                            try {
                                itemJson = execute(LIST_ITEMS_PAGE_QUERY, itemVars);
                            } catch (Exception e) {
                                if (isCancelled(e)) {
                                    throw e;
                                }
                                break;
                            }
                            JSONObject itemRoot = new JSONObject(itemJson);
                            JSONObject itemData = itemRoot.optJSONObject("data");
                            JSONObject listNode = itemData != null
                                    ? itemData.optJSONObject("node") : null;
                            JSONObject nextItems = listNode != null
                                    ? listNode.optJSONObject("items") : null;
                            if (nextItems == null) {
                                break;
                            }
                            collectRepos(nextItems, byName);
                            itemsPage = new PageInfo(nextItems);
                            itemsCursor = itemsPage.endCursor;
                        }
                        lists.add(new UserList(id, name, node.optString("description", ""),
                                new ArrayList<>(byName.values())));
                    }
                }
                PageInfo listsPage = new PageInfo(listsConn);
                listsCursor = listsPage.hasNextPage ? listsPage.endCursor : null;
                if (listsPage.hasNextPage && listsCursor == null) {
                    break; // 服务端称有下一页却没给游标：避免死循环，返回已取部分。
                }
            } while (listsCursor != null);
            return lists;
        }).doOnDispose(GraphQLClient::cancelActiveCall);
    }

    private static final String REPO_ID_QUERY =
            "query($owner: String!, $name: String!) {"
            + "  repository(owner: $owner, name: $name) {"
            + "    id"
            + "  }"
            + "}";

    /** 仓库的 GraphQL 节点 ID（updateUserListsForItem 要的是它，不是 REST 的数字 id）。 */
    public static Single<String> fetchRepositoryNodeId(String owner, String name) {
        return Single.fromCallable(() -> {
            JSONObject vars = new JSONObject();
            vars.put("owner", owner);
            vars.put("name", name);
            String json = execute(REPO_ID_QUERY, vars);
            // 去重-5：之前 new JSONObject(json) 写了两次（条件里一次、
            // 取值里一次）；一次解析后复用局部变量。
            JSONObject data = new JSONObject(json).optJSONObject("data");
            JSONObject repo = data != null ? data.optJSONObject("repository") : null;
            return repo != null ? repo.optString("id", "") : "";
        });
    }

    private static final String UPDATE_LISTS_MUTATION =
            "mutation($itemId: ID!, $listIds: [ID!]!) {"
            + "  updateUserListsForItem(input: {itemId: $itemId, listIds: $listIds}) {"
            + "    lists { id }"
            + "  }"
            + "}";

    /** 把一个仓库归属到给定的官方列表集合（空集合 = 移出全部官方列表）。 */
    public static Single<Boolean> updateListsForRepo(String nodeId, List<String> listIds) {
        return Single.fromCallable(() -> {
            JSONObject vars = new JSONObject();
            vars.put("itemId", nodeId);
            JSONArray ids = new JSONArray();
            for (String id : listIds) {
                ids.put(id);
            }
            vars.put("listIds", ids);
            execute(UPDATE_LISTS_MUTATION, vars);
            return true;
        });
    }

    private static final String CREATE_LIST_MUTATION =
            "mutation($name: String!, $isPrivate: Boolean) {"
            + "  createUserList(input: {name: $name, isPrivate: $isPrivate}) {"
            + "    list { id name description }"
            + "  }"
            + "}";

    /** 0.1.64：新建官方列表；isPrivate 省略时服务端默认公开。 */
    public static Single<UserList> createList(String name, boolean isPrivate) {
        return Single.fromCallable(() -> {
            JSONObject vars = new JSONObject();
            vars.put("name", name);
            vars.put("isPrivate", isPrivate);
            String json = execute(CREATE_LIST_MUTATION, vars);
            JSONObject data = new JSONObject(json).optJSONObject("data");
            JSONObject payload = data != null ? data.optJSONObject("createUserList") : null;
            JSONObject list = payload != null ? payload.optJSONObject("list") : null;
            if (list == null) {
                throw new RuntimeException("GraphQL: createUserList returned no list");
            }
            return new UserList(list.optString("id", ""), list.optString("name", name),
                    list.optString("description", ""), new ArrayList<>());
        });
    }

    /** 0.1.64：新建官方列表（公开，默认行为；与旧单参调用点一致）。 */
    public static Single<UserList> createList(String name) {
        return createList(name, false);
    }

    private static final String RENAME_LIST_MUTATION =
            "mutation($listId: ID!, $name: String!) {"
            + "  updateUserList(input: {listId: $listId, name: $name}) {"
            + "    list { id name description }"
            + "  }"
            + "}";

    /** 0.1.64：修改官方列表名称；组内仓库跟随，不用重新归属。 */
    public static Single<UserList> renameList(String listId, String newName) {
        return Single.fromCallable(() -> {
            JSONObject vars = new JSONObject();
            vars.put("listId", listId);
            vars.put("name", newName);
            String json = execute(RENAME_LIST_MUTATION, vars);
            JSONObject data = new JSONObject(json).optJSONObject("data");
            JSONObject payload = data != null ? data.optJSONObject("updateUserList") : null;
            JSONObject list = payload != null ? payload.optJSONObject("list") : null;
            if (list == null) {
                throw new RuntimeException("GraphQL: updateUserList returned no list");
            }
            return new UserList(list.optString("id", ""), list.optString("name", newName),
                    list.optString("description", ""), new ArrayList<>());
        });
    }

    private static final String DELETE_LIST_MUTATION =
            "mutation($listId: ID!) {"
            + "  deleteUserList(input: {listId: $listId}) {"
            + "    user { login }"
            + "  }"
            + "}";

    /**
     * 0.1.64：删除官方列表。只删列表与仓库关联，仓库的 Star 保留
     * （“删组即解散”），与 deleteUserList 的官方语义一致。
     */
    public static Single<Boolean> deleteList(String listId) {
        return Single.fromCallable(() -> {
            JSONObject vars = new JSONObject();
            vars.put("listId", listId);
            execute(DELETE_LIST_MUTATION, vars);
            return true;
        });
    }

    /**
     * 0.1.64：把一个仓库归属到给定的官方列表集合（空集合 = 移出全部官方列表）。
     * 供 StarMergedFragment（单仓/批量）与 RepositoryFragment（加星后入组）共用。
     */
    public static Single<Boolean> assignRepo(String fullName, List<String> listIds) {
        String[] parts = fullName.split("/", 2);
        if (parts.length != 2) {
            return Single.error(new RuntimeException("invalid repo full name"));
        }
        return fetchRepositoryNodeId(parts[0], parts[1])
                .flatMap(nodeId -> {
                    if (nodeId.isEmpty()) {
                        return Single.error(new RuntimeException("empty node id"));
                    }
                    return updateListsForRepo(nodeId, listIds);
                });
    }
}
