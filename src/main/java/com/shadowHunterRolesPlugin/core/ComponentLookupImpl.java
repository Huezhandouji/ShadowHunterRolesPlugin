package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.ComponentLookup;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * {@link ComponentLookup} 的独立适配器（包级私有；禁止 {@code RoleInstance} 直接 implements 端口）。
 * 反向引用只存在这里。
 * <p><b>阶段 10 · t55：本类成为"组件服务"的唯一实现点</b> —— 查找四件（类型第一个 / 类型全部 / id / 快照）
 * 直接转调 {@link ComponentRegistry}；动态三件（增 / 插位 / 删）在这里**编排**：
 * <pre>
 * add / insertAt : ① 校验（id 非空、描述符非空、**不在遍历窗口内**、下标合法）
 *                  ② 描述符 bindId + freeze ⇒ 拿到 (工厂, 提供类型, 必需依赖) 三元组
 *                  ③ **依赖预检**：必需依赖在容器内无人提供 ⇒ 拒绝（消息点名缺的类型）
 *                  ④ 取该 id 的服务集（servicesFactory，与装配期同一个工厂 ⇒ 组件拿到一对一端口）
 *                  ⑤ 构造 → ⑥ 注册/插位（连声明一起登记）→ ⑦ awake() → ⑧ start()
 *                  ⑨ 任一步失败 ⇒ 回滚（stop + 回收资源 + 移出容器）后原样抛出
 * remove         : ① 按 id 找（**添加顺序第一个**）→ ② **反向依赖检查**（P2）：有阻止者 ⇒ 记日志 + 抛异常
 *                  ③ stop() → ④ 回收该组件资源 → ⑤ 移出容器
 * </pre>
 * <p><b>阶段 10 · t67（用户新路线图第 1/2 条）</b>：
 * ① 校验里**删掉了"id 未被占用"那一条** ⇒ **同一 id 可添加多次** ✓（用户第 2 条）；
 * ② 新增 {@link #getAll(Class)} 转发；③ {@link #get(Class)} 的语义按真实行为（**添加顺序第一个**）写明。
 * <p><b>为什么服务集由工厂注入而不是本类自造</b>：服务集与组件**一对一**（冷却端口按 id 选表、定时器端口
 * 按 id 定位资源表）⇒ 必须与装配期走**同一条**构造路径（{@code RoleInstance#createServices}）。
 * <p><b>为什么删除前必须算反向依赖</b>（队长裁定 P2）：删掉一个被他人 {@code requires} 的组件后，
 * 运行期 {@code getComponent} 就取不到它 ⇒ "必需"会**静默失效**。反向依赖表**从声明现算**
 * （{@code ComponentRegistry#requiredBy}），不手工维护。
 * <p><b>可测性</b>：本类只依赖 {@code ComponentRegistry} + 一个 {@code String -> ComponentServices} 函数 +
 * 一个 {@link Logger}，**不碰 Bukkit** ⇒ 可以脱离服务器实例化并驱动（探针口径）。
 */
final class ComponentLookupImpl implements ComponentLookup {

    private final ComponentRegistry registry;
    private final Function<String, ComponentServices> servicesFactory;
    private final Logger logger;

    ComponentLookupImpl(ComponentRegistry registry,
                        Function<String, ComponentServices> servicesFactory,
                        Logger logger) {
        this.registry = registry;
        this.servicesFactory = servicesFactory;
        this.logger = logger;
    }

    @Override
    public <T> T get(Class<T> type) {
        return registry.get(type);
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        return registry.getAll(type);
    }

    @Override
    public RoleComponent getById(String id) {
        return registry.getById(id);
    }

    /**
     * **按 id 取全部**（阶段 11 · t77 · 用户裁定）：与 {@link #getAll(Class)} 对称。
     * <p><b>实现只用 {@code registry} 的公开读口</b>（{@code all()} 线性过滤）⇒ **不改 {@code core/dispatch/}**
     * （它在 out of scope）✓；顺序 = 容器当前序 = **添加顺序** ✓；无人符合 ⇒ **空列表** ✓；
     * {@code id == null} ⇒ 空列表（与 {@code getById(null) == null} 同口径：都不抛）✓。
     */
    @Override
    public List<RoleComponent> getAllById(String id) {
        if (id == null) {
            return List.of();
        }
        List<RoleComponent> matches = new ArrayList<>();
        for (RoleComponent component : registry.all()) {
            if (id.equals(component.getId())) {
                matches.add(component);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<RoleComponent> all() {
        return registry.all();
    }

    @Override
    public <T extends RoleComponent> T add(String id, RoleComponent.Specification<T> specification) {
        return insertAt(registry.size(), id, specification);
    }

    @Override
    public <T extends RoleComponent> T insertAt(int index, String id, RoleComponent.Specification<T> specification) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component ID cannot be null or empty.");
        }
        if (specification == null) {
            throw new NullPointerException("specification");
        }
        //护栏先于构造：遍历窗口内**不做任何构造**（否则会造出一个马上要回滚的实例）
        if (registry.isIterating()) {
            throw new IllegalStateException("Cannot add component '" + id + "' while the container is iterating "
                    + "(the framework is broadcasting component hooks); defer it until after the broadcast.");
        }
        if (index < 0 || index > registry.size()) {
            throw new IndexOutOfBoundsException("Component index " + index + " is out of range [0, " + registry.size() + "].");
        }
        //阶段 10 · t67（用户新路线图第 2 条）：**id 唯一性护栏已删除** —— 同一个 id 可以添加多次。
        //（旧写法在这里抛 "Component id already registered: " + id ✗；只删 ComponentRegistry 里那一条
        // 是不够的，因为运行期 add 走的是本方法 ⇒ 两处都必须放开 ✓）

        //声明来源 = 描述符（与装配期同一个 freeze() 快照）
        specification.bindId(id);
        RoleComponent.Specification.Snapshot snapshot = specification.freeze();
        ComponentRegistry.Declaration declaration =
                new ComponentRegistry.Declaration(id, snapshot.getProvidedType(), snapshot.getRequiredTypes());

        //依赖预检（与装配期同一口径：自己不算提供者）
        Map<String, Class<? extends RoleComponent>> missing = registry.missingProviders(declaration);
        if (!missing.isEmpty()) {
            String message = "Refused to add component '" + id + "' to role instance: it requires missing component type(s) "
                    + String.join(", ", missing.keySet()) + ".";
            logger.warning(message);
            throw new IllegalStateException(message);
        }

        ComponentServices services = servicesFactory.apply(id);
        T component = create(snapshot, id, services);
        //阶段 11 · t84：**运行期动态增也做创建后绑定**（与装配期同一个落点 `RoleInstance#bindOwnerPorts`）
        //⇒ F-1/F-2/F-3 三条"状态面按实例"的口径在**新增组件**上同样成立 ✓。
        //时机 = 构造返回之后、任何钩子（awake/start）之前 —— 组件可能一醒就起冷却 / 登记任务。
        RoleInstance.bindOwnerPorts(services, component);
        try {
            registry.insert(index, component, declaration);
            component.awake();
            component.start();
        } catch (Throwable failure) {
            //失败面干净：stop() + 回收资源 + 移出容器（全部幂等），再把原异常抛出去
            try {
                component.stop();
            } catch (Throwable ignored) {
                //回滚路径不得让"清理时的异常"覆盖真正的失败原因
            }
            registry.cancelAll(component);
            registry.remove(component);
            throw failure;
        }
        return component;
    }

    @Override
    public boolean remove(String id) {
        //阶段 10 · t67：id 可重复 ⇒ 目标是**添加顺序第一个**同 id 者（与 getById 同目标；
        //反向依赖表也按那一个现算 ⇒ 守卫保护的正是"会被删掉的那一个" ✓），其余同 id 者留在容器里
        RoleComponent component = registry.getById(id);
        if (component == null) {
            return false;
        }
        //P2：删除前先算反向依赖（从声明现算）—— 有阻止者 ⇒ 记日志 + 拒绝（不删、不留半态）
        Map<String, Class<? extends RoleComponent>> blockers = registry.requiredBy(id);
        if (!blockers.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            for (Map.Entry<String, Class<? extends RoleComponent>> blocker : blockers.entrySet()) {
                if (detail.length() > 0) {
                    detail.append("; ");
                }
                detail.append("component '").append(blocker.getKey())
                        .append("' (requires '").append(blocker.getValue().getName()).append("')");
            }
            String message = "Refused to remove component '" + id + "' from role instance: still required by "
                    + detail + ".";
            logger.warning(message);
            throw new IllegalStateException(message);
        }
        component.stop();
        registry.cancelAll(component);
        return registry.remove(component);
    }

    /** 描述符快照 → 组件实例（快照里的工厂类型是通配，这里收敛到 {@code T}）。 */
    @SuppressWarnings("unchecked")
    private static <T extends RoleComponent> T create(RoleComponent.Specification.Snapshot snapshot,
                                                      String id, ComponentServices services) {
        T component = (T) snapshot.getFactory().create(id, services);
        if (component == null) {
            throw new IllegalStateException("Component factory for '" + id + "' returned null.");
        }
        return component;
    }
}
