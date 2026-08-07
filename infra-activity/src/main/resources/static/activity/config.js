(() => {
    "use strict";

    const state = {
        components: [], templates: [], activities: [], currentForm: null,
        editingComponentId: null, editingTemplateId: null, editingActivityId: null, templateBindings: [],
        listPages: { components: 1, templates: 1, activities: 1 }
    };
    const RECORD_PAGE_SIZE = 8;
    const THEME_STORAGE_KEY = "infra_activity_theme";
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const componentNodes = document.getElementById("component-nodes");
    const templateNodes = document.getElementById("template-nodes");
    const nodeTemplate = document.getElementById("node-template");
    const notice = document.getElementById("notice");
    const onlineStatusDialog = document.getElementById("activity-online-status-dialog");
    const copyDialog = document.getElementById("activity-copy-dialog");
    const configurationDeleteDialog = document.getElementById("configuration-delete-dialog");
    const debugDialog = document.getElementById("activity-debug-dialog");
    const personalSettingsDialog = document.getElementById("personal-settings-dialog");
    let pendingActivityOnlineStatus = null;
    let pendingActivityCopyId = null;
    let pendingConfigurationDeletion = null;
    let pendingActivityDebugId = null;

    // 主题色仅保存在当前浏览器，切换后对同一站点下的配置页面立即生效。
    const applyTheme = (theme) => {
        const supportedThemes = ["emerald", "green", "orange", "red", "ocean", "violet"];
        const selectedTheme = supportedThemes.includes(theme) ? theme : "emerald";
        document.documentElement.dataset.theme = selectedTheme;
        document.querySelectorAll(".theme-option").forEach((button) => {
            const active = button.dataset.theme === selectedTheme;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, selectedTheme);
        } catch (_) {
            // 隐私模式等无法使用本地存储时仅保留当前页面的主题色。
        }
    };

    const restoreTheme = () => {
        try {
            applyTheme(window.localStorage.getItem(THEME_STORAGE_KEY) || "emerald");
        } catch (_) {
            applyTheme("emerald");
        }
    };

    const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (character) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", "\"": "&quot;"
    })[character]);

    // 按当前列表搜索框过滤记录，搜索不改变已加载的原始数据。
    const filterRecords = (records, searchInputId, searchableValues) => {
        const keyword = document.getElementById(searchInputId).value.trim().toLocaleLowerCase();
        if (!keyword) {
            return records;
        }
        return records.filter((record) => searchableValues(record)
            .some((value) => String(value ?? "").toLocaleLowerCase().includes(keyword)));
    };

    // 在筛选时同时展示匹配数与总数，避免搜索后误认为数据被删除。
    const renderRecordCount = (elementId, filteredCount, totalCount) => {
        document.getElementById(elementId).textContent = filteredCount === totalCount
            ? totalCount
            : `${filteredCount} / ${totalCount}`;
    };

    // 将较长的本地记录集合切分为稳定页面，搜索或删除后会自动回退到有效页码。
    const paginateRecords = (type, records) => {
        const totalPages = Math.max(1, Math.ceil(records.length / RECORD_PAGE_SIZE));
        const page = Math.min(Math.max(state.listPages[type], 1), totalPages);
        state.listPages[type] = page;
        const pagination = document.getElementById({
            components: "component-pagination",
            templates: "template-pagination",
            activities: "activity-pagination"
        }[type]);
        pagination.hidden = records.length <= RECORD_PAGE_SIZE;
        pagination.innerHTML = records.length <= RECORD_PAGE_SIZE ? "" : `
            <button class="pagination-button" type="button" data-page-type="${type}" data-page="${page - 1}" ${page === 1 ? "disabled" : ""}>上一页</button>
            <span class="pagination-summary">第 ${page} / ${totalPages} 页，共 ${records.length} 条</span>
            <button class="pagination-button" type="button" data-page-type="${type}" data-page="${page + 1}" ${page === totalPages ? "disabled" : ""}>下一页</button>`;
        const offset = (page - 1) * RECORD_PAGE_SIZE;
        return records.slice(offset, offset + RECORD_PAGE_SIZE);
    };

    // 为新建和编辑状态提供一致的视觉提示，避免误将更新操作当成新增操作。
    const setEditorMode = (type, editingId = null) => {
        const editing = editingId !== null;
        const labels = {
            component: ["新建", "正在创建新的可复用组件。", "编辑中", `正在编辑组件 #${editingId}，保存将覆盖原有配置。`],
            template: ["新建", "正在创建新的活动模板。", "编辑中", `正在编辑模板 #${editingId}，保存将覆盖原有配置。`],
            activity: ["新建", "正在创建新的活动配置。", "编辑中", `正在编辑活动 #${editingId}，保存将覆盖原有配置。`]
        };
        const [newMode, newStatus, editMode, editStatus] = labels[type];
        document.getElementById(`${type}-form`).classList.toggle("is-editing", editing);
        document.getElementById(`${type}-form-mode`).textContent = editing ? editMode : newMode;
        document.getElementById(`${type}-form-status`).textContent = editing ? editStatus : newStatus;
    };

    const showNotice = (message, error = false) => {
        notice.textContent = message;
        notice.hidden = false;
        notice.classList.toggle("is-error", error);
        window.clearTimeout(showNotice.timer);
        showNotice.timer = window.setTimeout(() => { notice.hidden = true; }, 5000);
    };

    // 根据永久有效开关显示或隐藏活动的开始、结束时间输入框，并补齐当天默认时间。
    const syncValidityFields = () => {
        const form = document.getElementById("activity-form");
        const permanent = form.validForever.checked;
        const timeFields = document.getElementById("activity-validity-fields");
        timeFields.hidden = permanent;
        form.validStartTime.disabled = permanent;
        form.validEndTime.disabled = permanent;
        if (!permanent) {
            const now = new Date();
            const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60 * 1000).toISOString().slice(0, 10);
            form.validStartTime.value ||= `${localDate}T00:00:00`;
            form.validEndTime.value ||= `${localDate}T23:59:59`;
        }
    };

    // 草稿活动只能保持下线，切换为草稿时自动重置并锁定上下线状态。
    const syncActivityOnlineStatus = () => {
        const form = document.getElementById("activity-form");
        const onlineStatus = form.onlineStatus;
        const draft = form.status.value === "DRAFT";
        if (draft) {
            onlineStatus.value = "OFFLINE";
        }
        onlineStatus.disabled = draft;
    };

    // 根据调试模式开关显示白名单和强制指定时间；白名单仅在启用时必填。
    const syncDebugFields = () => {
        const enabled = document.getElementById("activity-debug-mode").checked;
        const fields = document.getElementById("activity-debug-fields");
        const userIds = document.getElementById("activity-debug-user-ids");
        const forceTime = document.getElementById("activity-debug-force-time");
        fields.hidden = !enabled;
        userIds.required = enabled;
        userIds.disabled = !enabled;
        forceTime.disabled = !enabled;
    };

    // 将白名单用户 ID 转换为弹窗内易读的多行文本。
    const formatDebugUserIds = (userIds) => (userIds || []).join("\n");

    // 解析逗号、空格或换行分隔的用户 ID，并拒绝非正整数和不安全数值。
    const parseDebugUserIds = (source) => {
        const rawValues = source.split(/[\s,，]+/).map((value) => value.trim()).filter(Boolean);
        if (!rawValues.length) {
            throw new Error("启用调试模式时必须填写用户 ID 白名单。");
        }
        const userIds = rawValues.map((value) => {
            if (!/^\d+$/.test(value)) {
                throw new Error("用户 ID 白名单只能填写正整数。");
            }
            const userId = Number(value);
            if (!Number.isSafeInteger(userId) || userId <= 0) {
                throw new Error("用户 ID 白名单只能填写安全的正整数。");
            }
            return userId;
        });
        return [...new Set(userIds)];
    };

    // 打开指定活动的独立调试配置弹窗，不与活动主体编辑表单相互影响。
    const openActivityDebugDialog = (activityId) => {
        const activity = state.activities.find((item) => item.id === activityId);
        if (!activity) {
            showNotice("未找到要配置调试模式的活动。", true);
            return;
        }
        pendingActivityDebugId = activityId;
        document.getElementById("activity-debug-mode").checked = activity.debugMode === true;
        document.getElementById("activity-debug-user-ids").value = formatDebugUserIds(activity.debugUserIds);
        document.getElementById("activity-debug-force-time").value = toDateTimeInputValue(activity.debugForceTime);
        syncDebugFields();
        debugDialog.showModal();
    };

    // 将毫秒时间戳格式化为本地日期时间输入框需要的精确到秒格式。
    const toDateTimeInputValue = (timestamp) => {
        if (timestamp === null || timestamp === undefined || timestamp === "") {
            return "";
        }
        const date = new Date(Number(timestamp));
        const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000);
        return localDate.toISOString().slice(0, 19);
    };

    // 将毫秒时间戳转换为页面列表展示的本地时间文本。
    const formatValidityTime = (timestamp) => toDateTimeInputValue(timestamp).replace("T", " ");

    const request = async (path, options = {}) => {
        const headers = { Accept: "application/json", ...(options.headers || {}) };
        if (options.body) {
            headers["Content-Type"] = "application/json";
        }
        if (csrfToken && csrfHeader && options.method && options.method !== "GET") {
            headers[csrfHeader] = csrfToken;
        }
        const response = await fetch(`/api/activity${path}`, { credentials: "same-origin", ...options, headers });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || `请求失败：${response.status}`);
        }
        if (response.status === 204) {
            return null;
        }
        return response.json();
    };

    // 从动态字段树读取所有多选字段的无数组索引路径。
    const multiSelectFieldKeys = (fields) => new Set(fields.flatMap((field) => [
        ...(field.type === "MULTI_SELECT" ? [field.key] : []),
        ...multiSelectFieldKeys(field.children || [])
    ]));

    // 去除组件数组的数值索引，使数组内的多选字段可匹配字段定义路径。
    const normalizedArrayPath = (path) => path.split(".").filter((part) => !/^\d+$/.test(part)).join(".");

    // 将层级活动 JSON 展平为页面动态控件使用的点号字段路径。
    const flattenActivityValues = (values, multiSelectKeys = new Set()) => {
        const flattened = {};
        const append = (path, value) => {
            if (Array.isArray(value)) {
                if (multiSelectKeys.has(normalizedArrayPath(path))) {
                    flattened[path] = value;
                } else {
                    value.forEach((child, index) => {
                        if (child !== null && child !== undefined) {
                            append(`${path}.${index}`, child);
                        }
                    });
                }
            } else if (value && typeof value === "object") {
                Object.entries(value).forEach(([key, child]) => append(`${path}.${key}`, child));
            } else {
                flattened[path] = value;
            }
        };
        Object.entries(values || {}).forEach(([key, value]) => append(key, value));
        return flattened;
    };

    // 将动态控件的点号字段路径转换为对象和数组，供后端以层级 JSON 保存。
    const nestActivityValues = (values) => {
        const nested = {};
        Object.entries(values).forEach(([path, value]) => {
            const segments = path.split(".");
            let target = nested;
            segments.forEach((segment, index) => {
                const isLast = index === segments.length - 1;
                if (isLast) {
                    if (Array.isArray(target)) {
                        target[Number(segment)] = value;
                    } else {
                        target[segment] = value;
                    }
                    return;
                }
                const nextIsArray = /^\d+$/.test(segments[index + 1]);
                const child = nextIsArray ? [] : {};
                if (Array.isArray(target)) {
                    const arrayIndex = Number(segment);
                    target[arrayIndex] ||= child;
                    target = target[arrayIndex];
                } else {
                    target[segment] ||= child;
                    target = target[segment];
                }
            });
        });
        const compact = (value) => {
            if (Array.isArray(value)) {
                return value.filter((item) => item !== null && item !== undefined).map(compact);
            }
            if (value && typeof value === "object") {
                return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, compact(child)]));
            }
            return value;
        };
        return compact(nested);
    };

    const addNode = (container) => {
        const node = nodeTemplate.content.firstElementChild.cloneNode(true);
        container.appendChild(node);
        refreshNodeEditor(node);
        return node;
    };

    // 将服务端返回的递归节点定义回填为可继续编辑的页面节点。
    const populateNode = (container, definition) => {
        const node = addNode(container);
        node.querySelector(".node-key").value = definition.key;
        node.querySelector(".node-label").value = definition.label;
        node.querySelector(".node-type").value = definition.type;
        node.querySelector(".node-required").checked = definition.required;
        node.querySelector(".node-placeholder").value = definition.placeholder || "";
        node.querySelector(".node-options").value = (definition.options || [])
            .map((option) => `${option.value}:${option.label}`)
            .join("\n");
        refreshNodeEditor(node);
        node.querySelector(".node-component-id").value = definition.componentId ? String(definition.componentId) : "";
        node.querySelector(".node-component-mode").value = definition.componentMode || "SINGLE";
        const defaultField = node.querySelector(".node-default");
        if (definition.type === "MULTI_SELECT") {
            const selectedValues = new Set((definition.defaultValue || "").split(",").filter(Boolean));
            Array.from(defaultField.options).forEach((option) => { option.selected = selectedValues.has(option.value); });
        } else {
            defaultField.value = definition.defaultValue || "";
        }
        if (definition.type !== "COMPONENT") {
            (definition.children || []).forEach((child) => populateNode(node.querySelector(".child-nodes > .node-list"), child));
        }
    };

    // 恢复新建组件状态，并清空正在编辑的组件标识。
    const resetComponentForm = () => {
        const form = document.getElementById("component-form");
        form.reset();
        state.editingComponentId = null;
        form.code.disabled = false;
        document.getElementById("component-form-title").textContent = "新建组件";
        setEditorMode("component");
        document.getElementById("cancel-component-edit").hidden = true;
        componentNodes.replaceChildren();
        addNode(componentNodes);
    };

    // 进入编辑状态时保留组件编码，避免已保存活动的数据键发生变化。
    const editComponent = (componentId) => {
        const component = state.components.find((item) => item.id === componentId);
        if (!component) {
            showNotice("未找到要编辑的组件。", true);
            return;
        }
        const form = document.getElementById("component-form");
        state.editingComponentId = component.id;
        form.code.value = component.code;
        form.code.disabled = true;
        form.name.value = component.name;
        form.description.value = component.description || "";
        form.enabled.checked = component.enabled;
        componentNodes.replaceChildren();
        component.definition.nodes.forEach((node) => populateNode(componentNodes, node));
        document.getElementById("component-form-title").textContent = "编辑组件";
        setEditorMode("component", component.id);
        document.getElementById("cancel-component-edit").hidden = false;
        document.getElementById("component-form").scrollIntoView({ behavior: "smooth", block: "start" });
    };

    // 恢复新建模板状态，同时移除当前模板的所有回填字段和挂载记录。
    const resetTemplateForm = () => {
        const form = document.getElementById("template-form");
        form.reset();
        state.editingTemplateId = null;
        state.templateBindings = [];
        form.code.disabled = false;
        templateNodes.replaceChildren();
        document.getElementById("template-form-title").textContent = "新建模板";
        setEditorMode("template");
        document.getElementById("cancel-template-edit").hidden = true;
        renderTemplateBindings();
    };

    // 回填模板自身字段、普通输入项和所有组件挂载，供页面继续编辑。
    const editTemplate = (templateId) => {
        const template = state.templates.find((item) => item.id === templateId);
        if (!template) {
            showNotice("未找到要编辑的活动模板。", true);
            return;
        }
        const form = document.getElementById("template-form");
        state.editingTemplateId = template.id;
        form.code.value = template.code;
        form.code.disabled = true;
        form.name.value = template.name;
        form.description.value = template.description || "";
        form.enabled.checked = template.enabled;
        state.templateBindings = template.components.map((binding) => ({
            componentId: binding.component.id,
            mountKey: binding.mountKey,
            mountTitle: binding.mountTitle || binding.component.name,
            mountMode: binding.mountMode,
            required: binding.required
        }));
        templateNodes.replaceChildren();
        template.definition.nodes.forEach((node) => populateNode(templateNodes, node));
        document.getElementById("template-form-title").textContent = "编辑模板";
        setEditorMode("template", template.id);
        document.getElementById("cancel-template-edit").hidden = false;
        renderTemplateBindings();
        form.scrollIntoView({ behavior: "smooth", block: "start" });
    };

    // 恢复新建活动状态，并清除活动动态表单和编辑标识。
    const resetActivityForm = () => {
        const form = document.getElementById("activity-form");
        form.reset();
        state.editingActivityId = null;
        state.currentForm = null;
        syncValidityFields();
        syncActivityOnlineStatus();
        document.getElementById("activity-form-title").textContent = "创建活动";
        setEditorMode("activity");
        document.getElementById("cancel-activity-edit").hidden = true;
        document.getElementById("activity-dynamic-fields").innerHTML = '<p class="empty-state">请选择一个活动模板。</p>';
        renderTemplates();
    };

    // 回填已保存活动的基础信息和动态字段，组件数组按原索引恢复为多个实例。
    const editActivity = async (activityId) => {
        const activity = state.activities.find((item) => item.id === activityId);
        if (!activity) {
            showNotice("未找到要编辑的活动。", true);
            return;
        }
        const form = document.getElementById("activity-form");
        state.editingActivityId = activity.id;
        form.name.value = activity.name;
        form.status.value = activity.status;
        form.onlineStatus.value = activity.onlineStatus || "OFFLINE";
        form.validForever.checked = activity.validForever !== false;
        form.validStartTime.value = toDateTimeInputValue(activity.validStartTime);
        form.validEndTime.value = toDateTimeInputValue(activity.validEndTime);
        syncValidityFields();
        syncActivityOnlineStatus();
        renderTemplates();
        form.templateId.value = String(activity.templateId);
        document.getElementById("activity-form-title").textContent = "编辑活动";
        setEditorMode("activity", activity.id);
        document.getElementById("cancel-activity-edit").hidden = false;
        await loadTemplateForm(activity.values, true);
        form.scrollIntoView({ behavior: "smooth", block: "start" });
    };

    const parseOptions = (source) => source.value
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => {
            const separator = line.indexOf(":");
            return separator === -1
                ? { value: line, label: line }
                : { value: line.slice(0, separator).trim(), label: line.slice(separator + 1).trim() };
        });

    const collectNodes = (container) => Array.from(container.children)
        .filter((element) => element.classList.contains("component-node"))
        .map((element) => {
            const type = element.querySelector(".node-type").value;
            const componentId = element.querySelector(".node-component-id").value;
            const defaultField = element.querySelector(".node-default");
            const defaultValue = type === "GROUP" || type === "COMPONENT"
                ? null
                : type === "MULTI_SELECT"
                    ? Array.from(defaultField.selectedOptions).map((option) => option.value).join(",") || null
                    : defaultField.value.trim() || null;
            return {
                key: element.querySelector(".node-key").value.trim(),
                label: element.querySelector(".node-label").value.trim(),
                type,
                required: element.querySelector(".node-required").checked,
                placeholder: element.querySelector(".node-placeholder").value.trim() || null,
                defaultValue,
                options: type === "SELECT" || type === "MULTI_SELECT" ? parseOptions(element.querySelector(".node-options")) : [],
                componentId: type === "COMPONENT" && componentId ? Number(componentId) : null,
                componentMode: type === "COMPONENT" ? element.querySelector(".node-component-mode").value : "SINGLE",
                children: type === "COMPONENT" ? [] : collectNodes(element.querySelector(".child-nodes > .node-list"))
            };
        });

    // 将当前已保存组件填入所有子组件引用下拉框，并保留原有选择值。
    const refreshComponentReferenceChoices = () => {
        document.querySelectorAll(".node-component-id").forEach((select) => {
            const selected = select.value;
            const choices = state.components
                .filter((component) => component.id !== state.editingComponentId)
                .map((component) => `<option value="${component.id}">${escapeHtml(component.name)} (${escapeHtml(component.code)})${component.enabled ? "" : " · 已停用"}</option>`)
                .join("");
            select.innerHTML = `<option value="">请选择子组件</option>${choices}`;
            if (state.components.some((component) => String(component.id) === selected && component.id !== state.editingComponentId)) {
                select.value = selected;
            }
        });
    };

    const refreshNodeEditor = (node) => {
        const type = node.querySelector(".node-type").value;
        const optionsField = node.querySelector(".node-options-field");
        const componentFields = node.querySelector(".node-component-fields");
        const defaultField = node.querySelector(".node-default-field");
        const childNodes = node.querySelector(".child-nodes");
        const currentDefaults = Array.from(node.querySelector(".node-default")?.selectedOptions || []).map((option) => option.value);
        const currentDefault = currentDefaults[0] || node.querySelector(".node-default")?.value || "";
        const selectType = type === "SELECT" || type === "MULTI_SELECT";
        optionsField.hidden = !selectType;
        optionsField.classList.toggle("is-hidden", !selectType);
        componentFields.hidden = type !== "COMPONENT";
        componentFields.classList.toggle("is-hidden", type !== "COMPONENT");
        childNodes.hidden = type === "COMPONENT";
        childNodes.classList.toggle("is-hidden", type === "COMPONENT");
        defaultField.hidden = type === "GROUP" || type === "COMPONENT";
        defaultField.classList.toggle("is-hidden", type === "GROUP" || type === "COMPONENT");
        refreshComponentReferenceChoices();
        if (type === "GROUP" || type === "COMPONENT") {
            defaultField.innerHTML = '默认值<input class="node-default" value="">';
            return;
        }
        if (type === "SELECT") {
            const options = parseOptions(node.querySelector(".node-options"));
            defaultField.innerHTML = `默认选项<select class="node-default"><option value="">不设置默认值</option>${options
                .map((option) => `<option value="${escapeHtml(option.value)}" ${option.value === currentDefault ? "selected" : ""}>${escapeHtml(option.label)}</option>`)
                .join("")}</select>`;
            return;
        }
        if (type === "MULTI_SELECT") {
            const options = parseOptions(node.querySelector(".node-options"));
            defaultField.innerHTML = `默认选项<select class="node-default" multiple size="${Math.min(Math.max(options.length, 2), 5)}">${options
                .map((option) => `<option value="${escapeHtml(option.value)}" ${currentDefaults.includes(option.value) ? "selected" : ""}>${escapeHtml(option.label)}</option>`)
                .join("")}</select>`;
            return;
        }
        const inputType = type === "NUMBER" ? "number" : type === "DATE" ? "date" : type === "DATE_TIME" ? "datetime-local" : "text";
        const inputStep = type === "DATE_TIME" ? ' step="1"' : "";
        defaultField.innerHTML = `默认值<input class="node-default" type="${inputType}"${inputStep} value="${escapeHtml(currentDefault)}" placeholder="可选">`;
    };

    // 渲染模板中已添加组件的顺序和单项必填设置。
    const renderTemplateBindings = () => {
        document.getElementById("template-binding-count").textContent = `${state.templateBindings.length} 项`;
        document.getElementById("template-component-bindings").innerHTML = state.templateBindings.length
            ? state.templateBindings.map((binding, index) => {
                const componentOptions = state.components
                    .filter((component) => component.enabled || component.id === binding.componentId)
                    .map((component) => `<option value="${component.id}" ${component.id === binding.componentId ? "selected" : ""}>${escapeHtml(component.name)} (${escapeHtml(component.code)})</option>`)
                    .join("");
                return `<div class="template-binding">
                    <span class="template-binding-order">${index + 1}</span>
                    <label class="template-binding-component">组件类型<select class="template-binding-component-id" data-binding-index="${index}" required><option value="">请选择组件类型</option>${componentOptions}</select></label>
                    <label class="template-binding-key">挂载键<input class="template-binding-mount-key" type="text" data-binding-index="${index}" value="${escapeHtml(binding.mountKey)}" required pattern="[a-z][a-z0-9_]{0,63}" placeholder="basic_info"></label>
                    <label class="template-binding-title">挂载标题<input class="template-binding-mount-title" type="text" data-binding-index="${index}" value="${escapeHtml(binding.mountTitle)}" required maxlength="128" placeholder="基础信息"></label>
                    <label class="template-binding-mode">挂载形式<select class="template-binding-mount-mode" data-binding-index="${index}"><option value="SINGLE" ${binding.mountMode === "SINGLE" ? "selected" : ""}>单个组件</option><option value="ARRAY" ${binding.mountMode === "ARRAY" ? "selected" : ""}>组件数组</option></select></label>
                    <span class="template-binding-actions"><label><input class="template-binding-required" type="checkbox" data-binding-index="${index}" ${binding.required ? "checked" : ""}>必填</label><button class="remove-template-binding" type="button" data-binding-index="${index}" title="移除组件" aria-label="移除组件">&#215;</button></span>
                </div>`;
            }).join("")
            : '<p class="empty-state">添加组件后，在下方选择组件类型并填写挂载配置。</p>';
    };

    const renderComponents = () => {
        const matchedComponents = filterRecords(state.components, "component-search", (component) => [
            component.name, component.code, component.description, component.enabled ? "可用" : "已停用"
        ]);
        renderRecordCount("component-count", matchedComponents.length, state.components.length);
        const components = paginateRecords("components", matchedComponents);
        document.getElementById("component-list").innerHTML = components.length
            ? components.map((component) => `
                <article class="record">
                    <p class="record-title">${escapeHtml(component.name)}</p>
                    <p class="record-code">${escapeHtml(component.code)}</p>
                    <p class="record-copy">${escapeHtml(component.description || "未填写组件说明")}</p>
                    <div class="record-tags"><span class="record-tag">${component.definition.nodes.length} 个根节点</span><span class="record-tag">${component.enabled ? "可用" : "已停用"}</span></div>
                    <div class="record-actions"><button class="secondary-button edit-component" type="button" data-component-id="${component.id}">编辑</button><button class="secondary-button delete-configuration" type="button" data-configuration-type="component" data-configuration-id="${component.id}">删除</button></div>
                </article>`).join("")
            : `<p class="empty-state">${state.components.length ? "没有匹配的组件。" : "尚未创建组件。"}</p>`;

        renderTemplateBindings();
        refreshComponentReferenceChoices();
    };

    const renderTemplates = () => {
        const matchedTemplates = filterRecords(state.templates, "template-search", (template) => [
            template.name,
            template.code,
            template.description,
            template.enabled ? "可用" : "已停用",
            ...template.components.flatMap((binding) => [binding.component.name, binding.component.code, binding.mountKey, binding.mountTitle])
        ]);
        renderRecordCount("template-count", matchedTemplates.length, state.templates.length);
        const templates = paginateRecords("templates", matchedTemplates);
        document.getElementById("template-list").innerHTML = templates.length
            ? templates.map((template) => `
                <article class="record">
                    <p class="record-title">${escapeHtml(template.name)}</p>
                    <p class="record-code">${escapeHtml(template.code)}</p>
                    <p class="record-copy">${escapeHtml(template.description || "未填写模板说明")}</p>
                    <div class="record-tags"><span class="record-tag">${template.definition.nodes.length} 个普通输入项</span>${template.components.map((item) => `<span class="record-tag">${escapeHtml(item.component.name)}</span>`).join("") || '<span class="record-tag">未配置组件</span>'}</div>
                    <div class="record-actions"><button class="secondary-button edit-template" type="button" data-template-id="${template.id}">编辑</button><button class="secondary-button delete-configuration" type="button" data-configuration-type="template" data-configuration-id="${template.id}">删除</button></div>
                </article>`).join("")
            : `<p class="empty-state">${state.templates.length ? "没有匹配的活动模板。" : "尚未创建活动模板。"}</p>`;

        const select = document.getElementById("activity-template-select");
        const prior = select.value;
        const editingActivity = state.activities.find((activity) => activity.id === state.editingActivityId);
        const selectable = (template) => template.enabled || template.id === editingActivity?.templateId;
        select.innerHTML = '<option value="">请选择活动模板</option>' + state.templates
            .filter(selectable)
            .map((template) => `<option value="${template.id}">${escapeHtml(template.name)} (${escapeHtml(template.code)})</option>`)
            .join("");
        if (state.templates.some((template) => String(template.id) === prior && selectable(template))) {
            select.value = prior;
        }
    };

    const renderActivities = () => {
        const matchedActivities = filterRecords(state.activities, "activity-search", (activity) => [
            activity.name,
            activity.status,
            activity.onlineStatus === "ONLINE" ? "已上线" : "已下线",
            `模板 ${activity.templateId}`
        ]);
        renderRecordCount("activity-count", matchedActivities.length, state.activities.length);
        const activities = paginateRecords("activities", matchedActivities);
        document.getElementById("activity-list").innerHTML = activities.length
            ? activities.map((activity) => {
                const validity = activity.validForever !== false
                    ? "永久有效"
                    : `${formatValidityTime(activity.validStartTime)} 至 ${formatValidityTime(activity.validEndTime)}`;
                const online = activity.onlineStatus === "ONLINE";
                const debug = activity.debugMode === true;
                const debugTime = activity.debugForceTime ? ` · 强制时间 ${formatValidityTime(activity.debugForceTime)}` : "";
                return `
                <article class="record">
                    <p class="record-title">${escapeHtml(activity.name)}</p>
                    <div class="record-tags"><span class="record-tag">${escapeHtml(activity.status)}</span><span class="record-tag ${online ? "online-status-tag" : "offline-status-tag"}">${online ? "已上线" : "已下线"}</span><span class="record-tag">${escapeHtml(validity)}</span>${debug ? `<span class="record-tag debug-status-tag">调试模式 · ${(activity.debugUserIds || []).length} 人${escapeHtml(debugTime)}</span>` : ""}<span class="record-tag">模板 #${activity.templateId}</span><span class="record-tag">${Object.keys(activity.values).length} 项配置</span></div>
                    <div class="record-actions"><button class="record-icon-button configure-activity-debug" type="button" data-activity-id="${activity.id}" title="调试配置" aria-label="调试配置">⚙</button><button class="record-icon-button copy-activity" type="button" data-activity-id="${activity.id}" title="复制活动" aria-label="复制活动">⧉</button><button class="record-icon-button ${online ? "offline-activity" : "online-activity"} toggle-activity-online-status" type="button" data-activity-id="${activity.id}" data-online-status="${online ? "OFFLINE" : "ONLINE"}" title="${online ? "下线活动" : "上线活动"}" aria-label="${online ? "下线活动" : "上线活动"}">${online ? "↓" : "↑"}</button><button class="record-icon-button edit-activity" type="button" data-activity-id="${activity.id}" title="编辑活动" aria-label="编辑活动">✎</button><button class="secondary-button delete-configuration" type="button" data-configuration-type="activity" data-configuration-id="${activity.id}">删除</button></div>
                </article>`;
            }).join("")
            : `<p class="empty-state">${state.activities.length ? "没有匹配的活动。" : "尚未创建活动。"}</p>`;
        const draftActivityIds = new Set(activities
            .filter((activity) => activity.status === "DRAFT")
            .map((activity) => activity.id));
        document.querySelectorAll(".toggle-activity-online-status").forEach((button) => {
            button.hidden = button.dataset.onlineStatus === "ONLINE" && draftActivityIds.has(Number(button.dataset.activityId));
        });
    };

    // 通过专用接口切换上下线状态，避免快捷操作提交或覆盖动态表单配置。
    const updateActivityOnlineStatus = async (activityId, onlineStatus) => {
        try {
            const activity = await request(`/activities/${activityId}/online-status`, {
                method: "PATCH",
                body: JSON.stringify({ onlineStatus })
            });
            state.activities = state.activities.map((item) => item.id === activityId ? activity : item);
            if (state.editingActivityId === activityId) {
                document.getElementById("activity-form").onlineStatus.value = activity.onlineStatus;
                syncActivityOnlineStatus();
            }
            renderActivities();
            showNotice(activity.onlineStatus === "ONLINE" ? "活动已上线。" : "活动已下线。");
        } catch (error) {
            showNotice(error.message, true);
        }
    };

    // 复制活动后将副本直接插入当前列表，副本由服务端固定设置为草稿和下线状态。
    const copyActivity = async (activityId) => {
        try {
            const activity = await request(`/activities/${activityId}/copy`, { method: "POST" });
            state.activities = [activity, ...state.activities];
            renderActivities();
            showNotice("活动已复制为草稿并下线。");
        } catch (error) {
            showNotice(error.message, true);
        }
    };

    // 删除成功后重置可能正在编辑的目标，并重新读取三个列表的当前数据。
    const deleteConfiguration = async (type, id) => {
        const metadata = {
            component: { path: "/components", label: "组件" },
            template: { path: "/templates", label: "活动模板" },
            activity: { path: "/activities", label: "活动" }
        }[type];
        if (!metadata) {
            return;
        }
        try {
            await request(`${metadata.path}/${id}`, { method: "DELETE" });
            if (type === "component" && state.editingComponentId === id) {
                resetComponentForm();
            }
            if (type === "template" && state.editingTemplateId === id) {
                resetTemplateForm();
            }
            if (type === "activity" && state.editingActivityId === id) {
                resetActivityForm();
            }
            await loadAll();
            showNotice(`${metadata.label}已删除。`);
        } catch (error) {
            showNotice(error.message, true);
        }
    };

    // 根据待删配置类型填充统一确认弹窗，取消或关闭时不会执行删除。
    const openConfigurationDeleteDialog = (type, id) => {
        const metadata = {
            component: { list: state.components, label: "组件", message: "删除后，该组件的字段定义将无法恢复。" },
            template: { list: state.templates, label: "活动模板", message: "删除后，该模板及其组件挂载配置将无法恢复。" },
            activity: { list: state.activities, label: "活动", message: "删除后，该活动及其填写的配置将无法恢复。" }
        }[type];
        const target = metadata?.list.find((item) => item.id === id);
        if (!metadata || !target) {
            showNotice("未找到要删除的配置。", true);
            return;
        }
        pendingConfigurationDeletion = { type, id };
        document.getElementById("configuration-delete-dialog-title").textContent = `确认删除${metadata.label}`;
        document.getElementById("configuration-delete-dialog-message").textContent = metadata.message;
        document.getElementById("configuration-delete-dialog-preview").textContent = target.name;
        document.getElementById("configuration-delete-dialog-confirm").textContent = `确认删除${metadata.label}`;
        configurationDeleteDialog.showModal();
    };

    // 打开复制确认弹窗，确认前不创建活动副本。
    const openActivityCopyDialog = (activityId) => {
        const activity = state.activities.find((item) => item.id === activityId);
        if (!activity) {
            showNotice("未找到要复制的活动。", true);
            return;
        }
        pendingActivityCopyId = activityId;
        document.getElementById("activity-copy-dialog-preview").textContent = activity.name;
        copyDialog.showModal();
    };

    // 打开站内确认弹窗，并暂存待执行的活动状态变更。
    const openActivityOnlineStatusDialog = (activityId, onlineStatus) => {
        const activity = state.activities.find((item) => item.id === activityId);
        if (!activity) {
            showNotice("未找到要更新的活动。", true);
            return;
        }
        const online = onlineStatus === "ONLINE";
        pendingActivityOnlineStatus = { activityId, onlineStatus };
        onlineStatusDialog.dataset.onlineStatus = onlineStatus;
        document.getElementById("activity-online-status-dialog-title").textContent = `确认${online ? "上线" : "下线"}活动`;
        document.getElementById("activity-online-status-dialog-icon").textContent = online ? "↑" : "↓";
        document.getElementById("activity-online-status-dialog-message").textContent = online
            ? "上线后，活动将以“已上线”状态对外可用。"
            : "下线后，活动将不再以“已上线”状态对外可用。";
        document.getElementById("activity-online-status-dialog-preview").textContent = activity.name;
        document.getElementById("activity-online-status-dialog-confirm").textContent = `确认${online ? "上线" : "下线"}`;
        onlineStatusDialog.showModal();
    };

    // 将字段原始路径替换为当前子组件实例使用的实际路径。
    const resolveChildFieldKey = (child, parent, parentKey) => parentKey + child.key.slice(parent.key.length);

    // 从活动已保存字段中识别子组件数组的全部实例索引。
    const arrayIndexes = (values, fieldKey) => [...new Set(Object.keys(values)
        .filter((key) => key.startsWith(`${fieldKey}.`))
        .map((key) => key.slice(fieldKey.length + 1).split(".")[0])
        .filter((index) => /^\d+$/.test(index))
        .map(Number))].sort((left, right) => left - right);

    // 递归渲染一个子组件数组实例中的全部字段。
    const renderArrayItem = (field, index, fieldKey = field.key, values = {}) => `
        <section class="dynamic-component-item" data-array-item-index="${index}">
            <button class="remove-array-item" type="button">移除此项</button>
            ${field.children.map((child) => renderDynamicField(child, resolveChildFieldKey(child, field, `${fieldKey}.${index}`), values)).join("")}
        </section>`;

    // 递归渲染普通字段、分组、单个子组件和子组件数组。
    const renderDynamicField = (field, fieldKey = field.key, values = {}) => {
        if (field.type === "GROUP") {
            return `<section class="dynamic-group" style="--depth:${field.depth}"><h4>${escapeHtml(field.label)}</h4>${field.children.map((child) => renderDynamicField(child, resolveChildFieldKey(child, field, fieldKey), values)).join("")}</section>`;
        }
        if (field.type === "COMPONENT") {
            const indexes = arrayIndexes(values, fieldKey);
            const content = field.collection
                ? (indexes.length ? indexes.map((index) => renderArrayItem(field, index, fieldKey, values)).join("") : (field.required ? renderArrayItem(field, 0, fieldKey, values) : ""))
                : field.children.map((child) => renderDynamicField(child, resolveChildFieldKey(child, field, fieldKey), values)).join("");
            return `<section class="dynamic-component" data-component-field-key="${escapeHtml(fieldKey)}" data-schema-field-key="${escapeHtml(field.key)}">
                <header class="dynamic-component-header"><h4>${escapeHtml(field.label)}${field.required ? " *" : ""}</h4>${field.collection ? '<button class="secondary-button add-array-item" type="button">添加一项</button>' : ""}</header>
                <div class="dynamic-component-items">${content}</div>
            </section>`;
        }
        const required = field.required ? "required" : "";
        const label = `${escapeHtml(field.label)}${field.required ? " *" : ""}`;
        const value = Object.prototype.hasOwnProperty.call(values, fieldKey) ? values[fieldKey] : field.defaultValue;
        const defaultValue = escapeHtml(value ?? "");
        const common = `data-field-key="${escapeHtml(fieldKey)}" ${required} placeholder="${escapeHtml(field.placeholder || "")}"`;
        let control;
        if (field.type === "TEXTAREA") {
            control = `<textarea ${common} rows="3">${defaultValue}</textarea>`;
        } else if (field.type === "SELECT") {
            control = `<select data-field-key="${escapeHtml(fieldKey)}" ${required}><option value="">请选择</option>${field.options.map((option) => `<option value="${escapeHtml(option.value)}" ${option.value === String(value ?? "") ? "selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}</select>`;
        } else if (field.type === "MULTI_SELECT") {
            const selectedValues = Array.isArray(value)
                ? value.map(String)
                : String(value ?? field.defaultValue ?? "").split(",").filter(Boolean);
            control = `<select data-field-key="${escapeHtml(fieldKey)}" ${required} multiple size="${Math.min(Math.max(field.options.length, 2), 5)}">${field.options.map((option) => `<option value="${escapeHtml(option.value)}" ${selectedValues.includes(option.value) ? "selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}</select>`;
        } else {
            const inputType = field.type === "NUMBER" ? "number" : field.type === "DATE" ? "date" : field.type === "DATE_TIME" ? "datetime-local" : "text";
            const inputStep = field.type === "DATE_TIME" ? ' step="1"' : "";
            control = `<input type="${inputType}"${inputStep} ${common} value="${defaultValue}">`;
        }
        const children = field.children.map((child) => renderDynamicField(child, resolveChildFieldKey(child, field, fieldKey), values)).join("");
        return `<div class="dynamic-field" style="--depth:${field.depth}"><label>${label}${control}</label>${children}</div>`;
    };

    // 根据字段路径在动态表单定义中定位子组件数组的结构。
    const findDynamicField = (fields, key) => {
        for (const field of fields) {
            if (field.key === key) {
                return field;
            }
            const child = findDynamicField(field.children || [], key);
            if (child) {
                return child;
            }
        }
        return null;
    };

    const renderDynamicFields = (form, values = {}) => {
        state.currentForm = form;
        const container = document.getElementById("activity-dynamic-fields");
        if (!form.fields.length) {
            container.innerHTML = '<p class="empty-state">该模板没有可填写的输入字段。</p>';
            return;
        }
        container.innerHTML = form.fields.map((field) => renderDynamicField(field, field.key, values)).join("");
    };

    const loadTemplateForm = async (values = {}, storedValues = false) => {
        const templateId = document.getElementById("activity-template-select").value;
        if (!templateId) {
            state.currentForm = null;
            document.getElementById("activity-dynamic-fields").innerHTML = '<p class="empty-state">请选择一个活动模板。</p>';
            return;
        }
        try {
            const dynamicForm = await request(`/templates/${templateId}/form`);
            const formValues = storedValues ? flattenActivityValues(values, multiSelectFieldKeys(dynamicForm.fields)) : values;
            renderDynamicFields(dynamicForm, formValues);
        } catch (error) {
            showNotice(error.message, true);
        }
    };

    const loadAll = async () => {
        try {
            const [components, templates, activities] = await Promise.all([
                request("/components"), request("/templates"), request("/activities")
            ]);
            state.components = components;
            state.templates = templates;
            state.activities = activities;
            renderComponents();
            renderTemplates();
            renderActivities();
        } catch (error) {
            showNotice(error.message || "无法加载活动配置数据，请检查数据库连接。", true);
        }
    };

    document.getElementById("add-root-node").addEventListener("click", () => addNode(componentNodes));
    document.getElementById("open-personal-settings").addEventListener("click", (event) => {
        event.currentTarget.closest(".account-menu")?.removeAttribute("open");
        personalSettingsDialog.showModal();
    });
    document.querySelectorAll(".theme-option").forEach((button) => {
        button.addEventListener("click", () => applyTheme(button.dataset.theme));
    });
    document.getElementById("add-template-node").addEventListener("click", () => addNode(templateNodes));
    document.getElementById("cancel-component-edit").addEventListener("click", resetComponentForm);
    document.getElementById("cancel-template-edit").addEventListener("click", resetTemplateForm);
    document.getElementById("cancel-activity-edit").addEventListener("click", resetActivityForm);
    document.getElementById("activity-valid-forever").addEventListener("change", syncValidityFields);
    document.getElementById("activity-form").status.addEventListener("change", syncActivityOnlineStatus);
    document.getElementById("activity-debug-mode").addEventListener("change", syncDebugFields);
    document.getElementById("cancel-activity-debug").addEventListener("click", () => {
        debugDialog.close();
    });
    debugDialog.addEventListener("close", () => { pendingActivityDebugId = null; });
    document.getElementById("activity-debug-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            if (pendingActivityDebugId === null) {
                throw new Error("未指定需要更新调试配置的活动。");
            }
            const enabled = document.getElementById("activity-debug-mode").checked;
            const forceTimeInput = document.getElementById("activity-debug-force-time");
            const configuration = enabled
                ? {
                    enabled: true,
                    userIds: parseDebugUserIds(document.getElementById("activity-debug-user-ids").value),
                    forceTime: forceTimeInput.value ? new Date(forceTimeInput.value).getTime() : null
                }
                : { enabled: false, userIds: [], forceTime: null };
            const activity = await request(`/activities/${pendingActivityDebugId}/debug`, {
                method: "PATCH",
                body: JSON.stringify({
                    debugMode: configuration.enabled,
                    debugUserIds: configuration.userIds,
                    debugForceTime: configuration.forceTime
                })
            });
            state.activities = state.activities.map((item) => item.id === activity.id ? activity : item);
            pendingActivityDebugId = null;
            debugDialog.close();
            renderActivities();
            showNotice(configuration.enabled ? "活动调试模式已更新。" : "活动调试模式已关闭。");
        } catch (error) { showNotice(error.message, true); }
    });
    document.getElementById("component-list").addEventListener("click", (event) => {
        const deleteButton = event.target.closest(".delete-configuration");
        if (deleteButton) {
            openConfigurationDeleteDialog(deleteButton.dataset.configurationType, Number(deleteButton.dataset.configurationId));
            return;
        }
        const button = event.target.closest(".edit-component");
        if (button) {
            editComponent(Number(button.dataset.componentId));
        }
    });
    document.getElementById("template-list").addEventListener("click", (event) => {
        const deleteButton = event.target.closest(".delete-configuration");
        if (deleteButton) {
            openConfigurationDeleteDialog(deleteButton.dataset.configurationType, Number(deleteButton.dataset.configurationId));
            return;
        }
        const button = event.target.closest(".edit-template");
        if (button) {
            editTemplate(Number(button.dataset.templateId));
        }
    });
    document.getElementById("activity-list").addEventListener("click", async (event) => {
        const deleteButton = event.target.closest(".delete-configuration");
        if (deleteButton) {
            openConfigurationDeleteDialog(deleteButton.dataset.configurationType, Number(deleteButton.dataset.configurationId));
            return;
        }
        const copyButton = event.target.closest(".copy-activity");
        if (copyButton) {
            openActivityCopyDialog(Number(copyButton.dataset.activityId));
            return;
        }
        const debugButton = event.target.closest(".configure-activity-debug");
        if (debugButton) {
            openActivityDebugDialog(Number(debugButton.dataset.activityId));
            return;
        }
        const onlineStatusButton = event.target.closest(".toggle-activity-online-status");
        if (onlineStatusButton) {
            openActivityOnlineStatusDialog(Number(onlineStatusButton.dataset.activityId), onlineStatusButton.dataset.onlineStatus);
            return;
        }
        const button = event.target.closest(".edit-activity");
        if (button) {
            await editActivity(Number(button.dataset.activityId));
        }
    });
    // 分页操作只改变当前列表页码，不影响正在编辑的内容或其他列表。
    document.querySelectorAll(".record-pagination").forEach((pagination) => {
        pagination.addEventListener("click", (event) => {
            const button = event.target.closest(".pagination-button");
            if (!button || button.disabled) {
                return;
            }
            const type = button.dataset.pageType;
            state.listPages[type] = Number(button.dataset.page);
            ({ components: renderComponents, templates: renderTemplates, activities: renderActivities }[type])();
        });
    });
    // 搜索关键词变化时回到第一页，确保搜索结果不会停留在不存在的后续页面。
    [["component-search", "components", renderComponents], ["template-search", "templates", renderTemplates], ["activity-search", "activities", renderActivities]]
        .forEach(([inputId, type, render]) => document.getElementById(inputId).addEventListener("input", () => {
            state.listPages[type] = 1;
            render();
        }));
    // 仅当用户在弹窗中点击确认时，才执行已暂存的上下线状态变更。
    onlineStatusDialog.addEventListener("close", async () => {
        const pending = pendingActivityOnlineStatus;
        pendingActivityOnlineStatus = null;
        if (onlineStatusDialog.returnValue === "confirm" && pending) {
            await updateActivityOnlineStatus(pending.activityId, pending.onlineStatus);
        }
    });
    // 仅在确认复制后调用复制接口；取消和关闭弹窗均不创建副本。
    copyDialog.addEventListener("close", async () => {
        const activityId = pendingActivityCopyId;
        pendingActivityCopyId = null;
        if (copyDialog.returnValue === "confirm" && activityId !== null) {
            await copyActivity(activityId);
        }
    });
    // 仅在删除弹窗确认后执行删除；关闭弹窗会清空待删除目标。
    configurationDeleteDialog.addEventListener("close", async () => {
        const pending = pendingConfigurationDeletion;
        pendingConfigurationDeletion = null;
        if (configurationDeleteDialog.returnValue === "confirm" && pending) {
            await deleteConfiguration(pending.type, pending.id);
        }
    });
    document.getElementById("add-template-binding").addEventListener("click", () => {
        state.templateBindings.push({ componentId: null, mountKey: "", mountTitle: "", mountMode: "SINGLE", required: false });
        renderTemplateBindings();
    });
    document.getElementById("template-component-bindings").addEventListener("click", (event) => {
        const button = event.target.closest(".remove-template-binding");
        if (button) {
            state.templateBindings.splice(Number(button.dataset.bindingIndex), 1);
            renderTemplateBindings();
        }
    });
    document.getElementById("template-component-bindings").addEventListener("change", (event) => {
        if (event.target.classList.contains("template-binding-component-id")) {
            const binding = state.templateBindings[Number(event.target.dataset.bindingIndex)];
            binding.componentId = Number(event.target.value) || null;
            if (!binding.mountTitle.trim() && binding.componentId) {
                binding.mountTitle = state.components.find((component) => component.id === binding.componentId)?.name || "";
                event.target.closest(".template-binding").querySelector(".template-binding-mount-title").value = binding.mountTitle;
            }
        }
        if (event.target.classList.contains("template-binding-required")) {
            state.templateBindings[Number(event.target.dataset.bindingIndex)].required = event.target.checked;
        }
        if (event.target.classList.contains("template-binding-mount-mode")) {
            state.templateBindings[Number(event.target.dataset.bindingIndex)].mountMode = event.target.value;
        }
    });
    document.getElementById("template-component-bindings").addEventListener("input", (event) => {
        if (event.target.classList.contains("template-binding-mount-key")) {
            state.templateBindings[Number(event.target.dataset.bindingIndex)].mountKey = event.target.value;
        }
        if (event.target.classList.contains("template-binding-mount-title")) {
            state.templateBindings[Number(event.target.dataset.bindingIndex)].mountTitle = event.target.value;
        }
    });
    // 为组件和模板的节点构建区统一绑定新增、删除与类型切换行为。
    const bindNodeBuilder = (form) => {
        form.addEventListener("click", (event) => {
            const target = event.target;
            if (target.classList.contains("add-child-node")) {
                addNode(target.closest(".child-nodes").querySelector(".node-list"));
            }
            if (target.classList.contains("remove-node")) {
                target.closest(".component-node").remove();
            }
        });
        form.addEventListener("change", (event) => {
            if (event.target.classList.contains("node-type")) {
                refreshNodeEditor(event.target.closest(".component-node"));
            }
        });
        form.addEventListener("input", (event) => {
            if (event.target.classList.contains("node-options")) {
                const node = event.target.closest(".component-node");
                if (["SELECT", "MULTI_SELECT"].includes(node.querySelector(".node-type").value)) {
                    refreshNodeEditor(node);
                }
            }
        });
    };
    bindNodeBuilder(document.getElementById("component-form"));
    bindNodeBuilder(document.getElementById("template-form"));

    document.getElementById("component-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const definition = { nodes: collectNodes(componentNodes) };
        if (!definition.nodes.length) {
            showNotice("请至少添加一个输入节点或分组组件。", true);
            return;
        }
        try {
            const componentPath = state.editingComponentId ? `/components/${state.editingComponentId}` : "/components";
            await request(componentPath, {
                method: state.editingComponentId ? "PUT" : "POST",
                body: JSON.stringify({
                    code: form.code.value.trim(), name: form.name.value.trim(), description: form.description.value.trim() || null,
                    enabled: form.enabled.checked, definition
                })
            });
            const editing = state.editingComponentId !== null;
            resetComponentForm();
            showNotice(editing ? "组件已更新。" : "组件已保存。");
            await loadAll();
        } catch (error) { showNotice(error.message, true); }
    });

    document.getElementById("template-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const definition = { nodes: collectNodes(templateNodes) };
        if (!state.templateBindings.length && !definition.nodes.length) {
            showNotice("请至少添加一个活动组件或普通输入项。", true);
            return;
        }
        if (state.templateBindings.some((binding) => !binding.componentId)) {
            showNotice("请为每个挂载选择组件类型。", true);
            return;
        }
        if (state.templateBindings.some((binding) => !/^[a-z][a-z0-9_]{0,63}$/.test(binding.mountKey))) {
            showNotice("请为每个组件填写仅含小写字母、数字和下划线的挂载键。", true);
            return;
        }
        if (state.templateBindings.some((binding) => !binding.mountTitle.trim() || binding.mountTitle.trim().length > 128)) {
            showNotice("请为每个组件填写不超过 128 个字符的挂载标题。", true);
            return;
        }
        if (new Set(state.templateBindings.map((binding) => binding.mountKey)).size !== state.templateBindings.length) {
            showNotice("同一模板中的挂载键不能重复。", true);
            return;
        }
        try {
            const templatePath = state.editingTemplateId ? `/templates/${state.editingTemplateId}` : "/templates";
            await request(templatePath, {
                method: state.editingTemplateId ? "PUT" : "POST",
                body: JSON.stringify({
                    code: form.code.value.trim(), name: form.name.value.trim(), description: form.description.value.trim() || null,
                    enabled: form.enabled.checked, definition, components: state.templateBindings
                })
            });
            const editing = state.editingTemplateId !== null;
            resetTemplateForm();
            showNotice(editing ? "活动模板已更新。" : "活动模板已保存。");
            await loadAll();
        } catch (error) { showNotice(error.message, true); }
    });

    document.getElementById("activity-template-select").addEventListener("change", loadTemplateForm);
    document.getElementById("activity-dynamic-fields").addEventListener("click", (event) => {
        const addButton = event.target.closest(".add-array-item");
        if (addButton) {
            const component = addButton.closest(".dynamic-component");
            const field = findDynamicField(state.currentForm?.fields || [], component.dataset.schemaFieldKey);
            if (!field) {
                showNotice("未找到子组件数组定义。", true);
                return;
            }
            const items = component.querySelector(".dynamic-component-items");
            const indexes = Array.from(items.querySelectorAll(".dynamic-component-item"))
                .map((item) => Number(item.dataset.arrayItemIndex));
            const nextIndex = indexes.length ? Math.max(...indexes) + 1 : 0;
            items.insertAdjacentHTML("beforeend", renderArrayItem(field, nextIndex, component.dataset.componentFieldKey));
            return;
        }
        const removeButton = event.target.closest(".remove-array-item");
        if (removeButton) {
            removeButton.closest(".dynamic-component-item").remove();
        }
    });
    document.getElementById("activity-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        if (!state.currentForm) {
            showNotice("请先选择一个活动模板。", true);
            return;
        }
        const values = {};
        document.querySelectorAll("[data-field-key]").forEach((input) => {
            values[input.dataset.fieldKey] = input.multiple
                ? Array.from(input.selectedOptions).map((option) => option.value)
                : input.value;
        });
        const validForever = form.validForever.checked;
        if (!validForever && (!form.validStartTime.value || !form.validEndTime.value)) {
            showNotice("非永久有效的活动必须设置开始时间和结束时间。", true);
            return;
        }
        try {
            const activityPath = state.editingActivityId ? `/activities/${state.editingActivityId}` : "/activities";
            await request(activityPath, {
                method: state.editingActivityId ? "PUT" : "POST",
                body: JSON.stringify({
                    name: form.name.value.trim(), templateId: Number(form.templateId.value),
                    status: form.status.value, onlineStatus: form.onlineStatus.value, validForever,
                    validStartTime: validForever ? null : new Date(form.validStartTime.value).getTime(),
                    validEndTime: validForever ? null : new Date(form.validEndTime.value).getTime(),
                    values: nestActivityValues(values)
                })
            });
            const editing = state.editingActivityId !== null;
            resetActivityForm();
            showNotice(editing ? "活动配置已更新。" : "活动配置已保存。");
            await loadAll();
        } catch (error) { showNotice(error.message, true); }
    });

    addNode(componentNodes);
    restoreTheme();
    syncValidityFields();
    syncActivityOnlineStatus();
    syncDebugFields();
    loadAll();
})();
