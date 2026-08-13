(() => {
    "use strict";

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    const state = { taskTemplates: [], activityTemplates: [], activities: [], editingTemplateId: null };
    const notice = document.getElementById("notice");
    const taskTemplateForm = document.getElementById("task-template-form");
    const bindingTemplateSelect = document.getElementById("binding-template-select");
    const activitySelect = document.getElementById("activity-select");
    const themeStorageKey = "infra_activity_theme";
    const supportedThemes = ["emerald", "green", "orange", "red", "ocean", "violet"];
    const timezoneOptions = [
        { value: "Asia/Shanghai", label: "中国标准时间（UTC+08:00）" },
        { value: "Asia/Tokyo", label: "日本标准时间（UTC+09:00）" },
        { value: "Asia/Singapore", label: "新加坡时间（UTC+08:00）" },
        { value: "Asia/Dubai", label: "海湾标准时间（UTC+04:00）" },
        { value: "Europe/London", label: "伦敦时间（UTC+00:00 / 夏令时 UTC+01:00）" },
        { value: "America/New_York", label: "纽约时间（UTC-05:00 / 夏令时 UTC-04:00）" },
        { value: "America/Los_Angeles", label: "洛杉矶时间（UTC-08:00 / 夏令时 UTC-07:00）" },
        { value: "UTC", label: "协调世界时（UTC+00:00）" }
    ];
    const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character]);
    const renderTimezoneOptions = (selectedTimezone) => {
        const selected = selectedTimezone || "Asia/Shanghai";
        const options = timezoneOptions.some((item) => item.value === selected)
            ? timezoneOptions
            : [{ value: selected, label: `${selected}（已保存时区）` }, ...timezoneOptions];
        return options.map((item) => `<option value="${escapeHtml(item.value)}" ${item.value === selected ? "selected" : ""}>${escapeHtml(item.label)}</option>`).join("");
    };
    const showNotice = (message, error = false) => { notice.textContent = message; notice.hidden = false; notice.classList.toggle("is-error", error); window.setTimeout(() => { notice.hidden = true; }, 5000); };
    const formatTime = (value) => value ? new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(new Date(value)).replaceAll("/", "-") : "-";
    // 主题色仅保存在当前浏览器，并在任务页面与其他管理页面保持一致。
    const applyTheme = (theme) => {
        const selected = supportedThemes.includes(theme) ? theme : "emerald";
        document.documentElement.dataset.theme = selected;
        localStorage.setItem(themeStorageKey, selected);
        document.querySelectorAll(".theme-option").forEach((button) => {
            const active = button.dataset.theme === selected;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
    };

    const request = async (path, options = {}) => {
        const headers = { Accept: "application/json", ...(options.headers || {}) };
        if (options.body) headers["Content-Type"] = "application/json";
        if (csrfToken && csrfHeader && options.method && options.method !== "GET") headers[csrfHeader] = csrfToken;
        const response = await fetch(`/api/activity${path}`, { credentials: "same-origin", ...options, headers });
        if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || `请求失败：${response.status}`); }
        return response.status === 204 ? null : response.json();
    };

    const parseJson = (value, label) => { try { const parsed = JSON.parse(value || "{}"); if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") throw new Error(); return parsed; } catch (_) { throw new Error(`${label}必须是 JSON 对象`); } };
    const taskConfirmDialog = document.getElementById("task-confirm-dialog");
    const manualTriggerDialog = document.getElementById("task-manual-trigger-dialog");
    const manualTriggerForm = document.getElementById("task-manual-trigger-form");
    let pendingManualTriggerTaskId = null;
    document.querySelectorAll("dialog").forEach((dialog) => {
        dialog.addEventListener("click", (event) => {
            if (event.target === dialog) dialog.close("cancel");
        });
    });
    manualTriggerDialog.addEventListener("close", () => { pendingManualTriggerTaskId = null; });
    // 使用站内确认弹窗替代浏览器原生确认框，清晰说明操作影响。
    const confirmAction = (action, destructive = false) => new Promise((resolve) => {
        const closeHandler = () => {
            taskConfirmDialog.removeEventListener("close", closeHandler);
            resolve(taskConfirmDialog.returnValue === "confirm");
        };
        const openDialog = () => {
            taskConfirmDialog.classList.toggle("is-destructive", destructive);
            document.getElementById("task-confirm-dialog-icon").textContent = destructive ? "!" : "✓";
            document.getElementById("task-confirm-dialog-eyebrow").textContent = "操作确认";
            document.getElementById("task-confirm-dialog-title").textContent = `确认${action}`;
            document.getElementById("task-confirm-dialog-message").textContent = "确认后将立即生效，请确认当前配置无误。";
            document.getElementById("task-confirm-dialog-preview").textContent = destructive ? "此操作无法恢复，请谨慎确认。" : "确认后将立即执行本次操作。";
            const button = document.getElementById("task-confirm-dialog-confirm");
            button.value = "confirm";
            button.textContent = `确认${action}`;
            taskConfirmDialog.returnValue = "";
            taskConfirmDialog.showModal();
        };
        taskConfirmDialog.addEventListener("close", closeHandler);
        openDialog();
    });
    // 手动触发先收集本次执行说明，再进入统一的双阶段确认，避免使用浏览器原生输入框。
    const openManualTriggerDialog = (taskId) => {
        pendingManualTriggerTaskId = taskId;
        manualTriggerForm.reset();
        manualTriggerDialog.showModal();
        window.setTimeout(() => document.getElementById("task-manual-trigger-reason").focus(), 0);
    };
    const submitManualTrigger = async (taskId, reason) => {
        if (!(await confirmAction("手动触发任务"))) return;
        try {
            await request(`/tasks/${taskId}/trigger`, { method: "POST", body: JSON.stringify({ reason: reason || null }) });
            showNotice("任务已触发，执行结果已记录。");
            await loadActivityTasks();
            await showExecutions(taskId);
        } catch (error) {
            showNotice(error.message, true);
        }
    };

    const renderTemplateOptions = (selected) => `<option value="">选择任务定义</option>${state.taskTemplates.map((item) => `<option value="${item.id}" ${Number(selected) === item.id ? "selected" : ""}>${escapeHtml(item.name)} · ${escapeHtml(item.handler_type)}</option>`).join("")}`;
    const renderSelectors = () => {
        bindingTemplateSelect.innerHTML = `<option value="">选择活动模板</option>${state.activityTemplates.map((item) => `<option value="${item.id}">${escapeHtml(item.name)} (${escapeHtml(item.code)})</option>`).join("")}`;
        activitySelect.innerHTML = `<option value="">选择活动查看实例任务</option>${state.activities.map((item) => `<option value="${item.id}">${escapeHtml(item.name)} #${item.id}</option>`).join("")}`;
    };
    const renderTaskTemplates = () => {
        const query = document.getElementById("task-template-search").value.trim().toLowerCase();
        const templates = state.taskTemplates.filter((item) => [item.name, item.handler_type, item.description].some((value) => String(value || "").toLowerCase().includes(query)));
        document.getElementById("task-template-count").textContent = String(templates.length);
        document.getElementById("task-template-list").innerHTML = templates.length ? templates.map((item) => `<article class="task-definition-card"><div class="task-definition-main"><div class="task-definition-heading"><h4>${escapeHtml(item.name)}</h4><span class="${item.enabled ? "status-chip pending" : "status-chip cancelled"}">${item.enabled ? "可用" : "停用"}</span></div><div class="task-definition-meta"><span>处理器：${escapeHtml(item.handler_type)}</span></div><p>${escapeHtml(item.description || "暂未填写任务说明")}</p></div><div class="task-definition-actions"><button class="task-definition-edit" type="button" data-edit-template="${item.id}">编辑</button><button class="task-definition-delete" type="button" data-delete-template="${item.id}">删除</button></div></article>`).join("") : '<p class="empty-state">暂无任务定义。</p>';
    };
    const resetTemplateForm = () => { state.editingTemplateId = null; taskTemplateForm.reset(); taskTemplateForm.maxRetryCount.value = "3"; taskTemplateForm.retryIntervalMillis.value = "60000"; taskTemplateForm.defaultParameters.value = "{}"; taskTemplateForm.enabled.checked = true; taskTemplateForm.classList.remove("is-editing"); document.getElementById("task-template-heading").textContent = "新建任务定义"; document.getElementById("cancel-template-edit").hidden = true; };
    const editTaskTemplate = (id) => {
        const item = state.taskTemplates.find((template) => template.id === id); if (!item) return;
        state.editingTemplateId = id; taskTemplateForm.name.value = item.name; taskTemplateForm.handlerType.value = item.handler_type; taskTemplateForm.description.value = item.description || ""; taskTemplateForm.defaultParameters.value = JSON.stringify(item.default_parameters || {}, null, 2); taskTemplateForm.maxRetryCount.value = item.max_retry_count; taskTemplateForm.retryIntervalMillis.value = item.retry_interval_millis; taskTemplateForm.enabled.checked = item.enabled; taskTemplateForm.classList.add("is-editing"); document.getElementById("task-template-heading").textContent = "编辑任务定义"; document.getElementById("cancel-template-edit").hidden = false; taskTemplateForm.scrollIntoView({ behavior: "smooth", block: "start" });
    };

    const triggerConfigMarkup = (type, config = {}) => {
        if (type === "FIXED_TIMES") return `<label class="wide">指定时间，每行一个<textarea class="config-fixed-times" rows="3" placeholder="2026-08-09 10:00:00&#10;2026-08-10 10:00:00">${escapeHtml((config.fixed_times || []).map((time) => formatTime(time)).join("\n"))}</textarea><small class="field-hint">每个时间点都会各执行一次；已过期的时间不会再次执行。</small></label>`;
        if (type === "CRON") return `<label>计算时区<select class="config-timezone" required>${renderTimezoneOptions(config.timezone)}</select><small class="field-hint">Cron 按所选 IANA 时区计算，括号内展示标准时区偏移。</small></label><label class="wide">Cron 表达式<input class="config-cron" required placeholder="0 0 0/2 * * *" value="${escapeHtml(config.cron || "")}"><small class="field-hint">使用 Spring 六段式 Cron，例如每两小时执行一次：0 0 0/2 * * *。</small></label><section class="cron-preview wide" aria-live="polite"><strong>后续执行预览</strong><p class="cron-preview-content">填写 Cron 表达式后展示未来 5 次执行时间。</p></section>`;
        if (type === "ACTIVITY_START_OFFSET" || type === "ACTIVITY_END_OFFSET") return `<label>偏移量（毫秒）<input class="config-offset" type="number" required value="${escapeHtml(config.offset_millis ?? 0)}"><small class="field-hint">正数表示开始或结束后的延迟，负数表示提前执行。</small></label>`;
        if (type === "INTERVAL_WINDOW") return `<label>执行间隔（毫秒）<input class="config-interval" type="number" min="1" required value="${escapeHtml(config.interval_millis || 7200000)}"><small class="field-hint">例如 7200000 表示每两小时执行一次。</small></label><label>窗口开始偏移（毫秒）<input class="config-window-start" type="number" value="${escapeHtml(config.window_start_offset_millis ?? 0)}"><small class="field-hint">相对活动开始时间，默认从活动开始时执行。</small></label><label>窗口结束偏移（毫秒）<input class="config-window-end" type="number" value="${escapeHtml(config.window_end_offset_millis ?? 0)}"><small class="field-hint">相对活动结束时间，默认在活动结束时停止。</small></label>`;
        return '<p class="binding-hint wide">该任务只可由运营人员在活动任务列表中手动触发。</p>';
    };
    const renumberBindings = () => document.querySelectorAll(".task-binding").forEach((node, index) => { node.querySelector(".binding-order").textContent = String(index + 1); });
    const addBinding = (binding = {}) => {
        const node = document.getElementById("task-binding-template").content.firstElementChild.cloneNode(true);
        node.querySelector(".binding-template").innerHTML = renderTemplateOptions(binding.task_template_id);
        node.querySelector(".binding-code").value = binding.code || ""; node.querySelector(".binding-name").value = binding.name || "";
        const type = binding.trigger_type || "MANUAL"; node.querySelector(".binding-trigger-type").value = type; node.querySelector(".trigger-config").innerHTML = triggerConfigMarkup(type, binding.trigger_config || {}); node.querySelector(".binding-parameters").value = JSON.stringify(binding.parameter_overrides || {}, null, 2); node.querySelector(".binding-enabled").checked = binding.enabled ?? true;
        node.querySelector(".binding-trigger-type").addEventListener("change", (event) => { node.querySelector(".trigger-config").innerHTML = triggerConfigMarkup(event.target.value); bindTriggerConfigEvents(node); });
        bindTriggerConfigEvents(node);
        node.querySelector(".remove-task-binding").addEventListener("click", () => { node.remove(); renumberBindings(); }); document.getElementById("task-bindings").append(node); renumberBindings();
    };
    // Cron 预览由服务端计算，保证预览结果与任务调度器使用同一套表达式语义。
    const bindTriggerConfigEvents = (node) => {
        const cronInput = node.querySelector(".config-cron");
        const timezoneSelect = node.querySelector(".config-timezone");
        const preview = node.querySelector(".cron-preview-content");
        if (!cronInput || !timezoneSelect || !preview) return;
        let debounceTimer = null;
        const refreshPreview = async () => {
            const cron = cronInput.value.trim();
            if (!cron) {
                preview.textContent = "填写 Cron 表达式后展示未来 5 次执行时间。";
                preview.classList.remove("is-error");
                return;
            }
            preview.textContent = "正在计算后续执行时间...";
            preview.classList.remove("is-error");
            try {
                const response = await request("/tasks/cron/preview", {
                    method: "POST",
                    body: JSON.stringify({ cron, timezone: timezoneSelect.value })
                });
                preview.textContent = response.next_times?.length
                    ? response.next_times.map(formatTime).join("\n")
                    : "该表达式没有可计算的后续触发时间。";
            } catch (error) {
                preview.textContent = error.message || "Cron 表达式不合法。";
                preview.classList.add("is-error");
            }
        };
        const schedulePreview = () => {
            window.clearTimeout(debounceTimer);
            debounceTimer = window.setTimeout(refreshPreview, 350);
        };
        cronInput.addEventListener("input", schedulePreview);
        timezoneSelect.addEventListener("change", refreshPreview);
        refreshPreview();
    };
    const readBinding = (node) => {
        const type = node.querySelector(".binding-trigger-type").value; const config = {};
        if (type === "FIXED_TIMES") { config.fixed_times = node.querySelector(".config-fixed-times").value.split(/\n|,/).map((value) => value.trim()).filter(Boolean).map((value) => { const time = new Date(value.replace(" ", "T")).getTime(); if (Number.isNaN(time)) throw new Error("指定时间格式不正确"); return time; }); }
        if (type === "CRON") { config.cron = node.querySelector(".config-cron").value.trim(); config.timezone = node.querySelector(".config-timezone").value.trim() || "Asia/Shanghai"; }
        if (type === "ACTIVITY_START_OFFSET" || type === "ACTIVITY_END_OFFSET") config.offset_millis = Number(node.querySelector(".config-offset").value);
        if (type === "INTERVAL_WINDOW") { config.interval_millis = Number(node.querySelector(".config-interval").value); config.window_start_offset_millis = Number(node.querySelector(".config-window-start").value); config.window_end_offset_millis = Number(node.querySelector(".config-window-end").value); }
        return { task_template_id: Number(node.querySelector(".binding-template").value), code: node.querySelector(".binding-code").value.trim(), name: node.querySelector(".binding-name").value.trim(), trigger_type: type, trigger_config: config, parameter_overrides: parseJson(node.querySelector(".binding-parameters").value, "参数覆盖"), enabled: node.querySelector(".binding-enabled").checked };
    };
    const loadBindings = async () => { document.getElementById("task-bindings").innerHTML = ""; const id = bindingTemplateSelect.value; if (!id) return; try { (await request(`/tasks/bindings/${id}`)).forEach(addBinding); } catch (error) { showNotice(error.message, true); } };
    const loadActivityTasks = async () => { document.getElementById("execution-panel").hidden = true; const id = activitySelect.value; const list = document.getElementById("activity-task-list"); if (!id) { list.innerHTML = '<p class="empty-state">选择活动后查看上线时生成的实际任务。</p>'; return; } try { const tasks = await request(`/tasks/activities/${id}`); list.innerHTML = tasks.length ? tasks.map((task) => `<article class="runtime-task"><div><h3>${escapeHtml(task.name)} <span class="status-chip ${task.status.toLowerCase()}">${escapeHtml(task.status)}</span></h3><p>${escapeHtml(task.code)} · ${escapeHtml(task.handler_type)} · ${escapeHtml(task.trigger_type)}<br>下次触发：${formatTime(task.next_trigger_time)}　最近触发：${formatTime(task.last_trigger_time)}　重试：${task.retry_count}</p></div><div class="runtime-actions"><button class="secondary-button" data-executions="${task.id}" type="button">记录</button><button class="primary-button" data-trigger="${task.id}" type="button" ${task.status === "CANCELLED" ? "disabled" : ""}>手动触发</button></div></article>`).join("") : '<p class="empty-state">该活动尚未生成任务。请确认活动已启用且已上线，并在模板中配置任务。</p>'; } catch (error) { showNotice(error.message, true); } };
    const showExecutions = async (taskId) => { try { const rows = await request(`/tasks/${taskId}/executions`); const list = document.getElementById("execution-list"); list.innerHTML = rows.length ? rows.map((row) => `<article class="execution-record ${row.status.toLowerCase()}"><span class="status-chip ${row.status.toLowerCase()}">${escapeHtml(row.status)}</span><div><strong>${escapeHtml(row.trigger_source)} · 第 ${row.attempt_no} 次</strong><p>触发：${formatTime(row.trigger_time)}　开始：${formatTime(row.start_time)}　结束：${formatTime(row.end_time)}${row.error_message ? `<br>错误：${escapeHtml(row.error_message)}` : ""}${row.result ? `<br>结果：${escapeHtml(JSON.stringify(row.result))}` : ""}</p></div></article>`).join("") : '<p class="empty-state">暂无执行记录。</p>'; document.getElementById("execution-panel").hidden = false; } catch (error) { showNotice(error.message, true); } };
    const loadAll = async () => { try { [state.taskTemplates, state.activityTemplates, state.activities] = await Promise.all([request("/tasks/templates"), request("/templates"), request("/activities")]); renderTemplateOptions(); renderSelectors(); renderTaskTemplates(); await loadActivityTasks(); } catch (error) { showNotice(error.message, true); } };

    taskTemplateForm.addEventListener("submit", async (event) => { event.preventDefault(); try { const payload = { name: taskTemplateForm.name.value.trim(), handler_type: taskTemplateForm.handlerType.value, description: taskTemplateForm.description.value.trim() || null, default_parameters: parseJson(taskTemplateForm.defaultParameters.value, "默认参数"), max_retry_count: Number(taskTemplateForm.maxRetryCount.value), retry_interval_millis: Number(taskTemplateForm.retryIntervalMillis.value), enabled: taskTemplateForm.enabled.checked }; const editing = state.editingTemplateId; if (editing && !(await confirmAction("更新任务定义"))) return; await request(editing ? `/tasks/templates/${editing}` : "/tasks/templates", { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) }); resetTemplateForm(); showNotice(editing ? "任务定义已更新。" : "任务定义已保存。"); await loadAll(); } catch (error) { showNotice(error.message, true); } });
    document.getElementById("cancel-template-edit").addEventListener("click", resetTemplateForm);
    document.getElementById("task-template-search").addEventListener("input", renderTaskTemplates);
    document.getElementById("task-template-list").addEventListener("click", async (event) => { const edit = event.target.closest("[data-edit-template]"); const remove = event.target.closest("[data-delete-template]"); if (edit) editTaskTemplate(Number(edit.dataset.editTemplate)); if (remove && await confirmAction("删除任务模板", true)) { try { await request(`/tasks/templates/${remove.dataset.deleteTemplate}`, { method: "DELETE" }); showNotice("任务模板已删除。"); await loadAll(); } catch (error) { showNotice(error.message, true); } } });
    bindingTemplateSelect.addEventListener("change", loadBindings); document.getElementById("add-task-binding").addEventListener("click", () => addBinding());
    document.getElementById("save-task-bindings").addEventListener("click", async () => { const templateId = bindingTemplateSelect.value; if (!templateId) { showNotice("请先选择活动模板。", true); return; } try { const tasks = Array.from(document.querySelectorAll(".task-binding")).map(readBinding); if (tasks.some((task) => !task.task_template_id || !task.code || !task.name)) throw new Error("请完整填写每个任务的模板、编码和名称"); if (new Set(tasks.map((task) => task.code)).size !== tasks.length) throw new Error("同一活动模板内的任务编码不能重复"); if (!(await confirmAction("更新活动模板任务"))) return; await request(`/tasks/bindings/${templateId}`, { method: "PUT", body: JSON.stringify({ tasks }) }); showNotice("活动模板任务已保存，并已刷新上线活动的任务快照。"); await loadActivityTasks(); } catch (error) { showNotice(error.message, true); } });
    activitySelect.addEventListener("change", loadActivityTasks); document.getElementById("activity-task-list").addEventListener("click", async (event) => { const execution = event.target.closest("[data-executions]"); const trigger = event.target.closest("[data-trigger]"); if (execution) return showExecutions(Number(execution.dataset.executions)); if (trigger) openManualTriggerDialog(Number(trigger.dataset.trigger)); });
    manualTriggerForm.addEventListener("submit", async (event) => { event.preventDefault(); const taskId = pendingManualTriggerTaskId; manualTriggerDialog.close(); pendingManualTriggerTaskId = null; if (taskId != null) await submitManualTrigger(taskId, document.getElementById("task-manual-trigger-reason").value.trim()); });
    document.getElementById("cancel-task-manual-trigger").addEventListener("click", () => { pendingManualTriggerTaskId = null; manualTriggerDialog.close(); });
    document.getElementById("close-executions").addEventListener("click", () => { document.getElementById("execution-panel").hidden = true; });
    document.getElementById("open-personal-settings").addEventListener("click", () => document.getElementById("personal-settings-dialog").showModal());
    document.querySelectorAll(".theme-option").forEach((button) => button.addEventListener("click", () => applyTheme(button.dataset.theme)));
    applyTheme(localStorage.getItem(themeStorageKey) || "emerald");
    loadAll();
})();
