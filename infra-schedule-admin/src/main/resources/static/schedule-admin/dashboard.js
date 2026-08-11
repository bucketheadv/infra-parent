(() => {
    /** 页面调用的调度 HTTP 协议路径，集中维护以避免散落的 URL 字面量。 */
    const SchedulePaths = Object.freeze({
        apiRoot: "/infra/schedule",
        jobs: "/jobs",
        job: id => `/jobs/${encodeURIComponent(id)}`,
        trigger: id => `/jobs/${encodeURIComponent(id)}/trigger`,
        status: id => `/jobs/${encodeURIComponent(id)}/status`,
        logs: id => `/jobs/${encodeURIComponent(id)}/logs`,
        queryLogs: "/logs",
        nextTriggers: id => `/jobs/${encodeURIComponent(id)}/next-triggers`,
        nextTriggersPreview: "/jobs/next-triggers/preview",
        executors: "/executors",
        executor: id => `/executors/${encodeURIComponent(id)}`,
        executorStatus: id => `/executors/${encodeURIComponent(id)}/status`
    });
    const page = document.body.dataset.page || "jobs";
    const toast = document.querySelector("#toast");
    const confirmDialog = document.querySelector("#confirm-dialog");
    /** 与调度中心 heartbeatTimeoutMillis 默认值对齐。 */
    const HEARTBEAT_TIMEOUT_MS = 30_000;
    let toastTimer;
    let confirmResolver = null;

    /** 执行器是否仍在心跳有效期内（且未手动禁用）。 */
    function isExecutorOnline(executor, now = Date.now()) {
        return executor.status === "ENABLED" && now - executor.lastHeartbeatTime <= HEARTBEAT_TIMEOUT_MS;
    }

    /** 发送同源 JSON 请求，并将后端可读错误转换为异常。 */
    async function request(path, options = {}) {
        const response = await fetch(`${SchedulePaths.apiRoot}${path}`, {
            headers: { "Content-Type": "application/json", ...(options.headers || {}) },
            ...options
        });
        if (!response.ok) {
            const body = await response.text();
            throw new Error(body || `请求失败（${response.status}）`);
        }
        if (response.status === 204) return null;
        return response.json();
    }

    /** 将动态文本转换为安全的 HTML 文本，避免任务参数或错误消息注入页面。 */
    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>'"]/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", "\"": "&quot;" })[character]);
    }

    /** 使用本地时区格式化毫秒时间戳。 */
    function formatTime(timestamp) {
        return timestamp ? new Intl.DateTimeFormat("zh-CN", { dateStyle: "short", timeStyle: "medium" }).format(new Date(timestamp)) : "—";
    }

    /** 格式化执行耗时；不足 1 秒显示毫秒，否则显示秒。 */
    function formatDuration(durationMillis) {
        if (durationMillis == null || durationMillis === "") return "—";
        const millis = Number(durationMillis);
        if (!Number.isFinite(millis) || millis < 0) return "—";
        if (millis < 1000) return `${millis}ms`;
        return `${(millis / 1000).toFixed(millis % 1000 === 0 ? 0 : 2)}s`;
    }

    /** 将 Date 转为 datetime-local 控件值。 */
    function toDateTimeLocalValue(date) {
        const pad = value => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
    }

    /** 将 datetime-local 控件值转为毫秒时间戳。 */
    function fromDateTimeLocalValue(value) {
        if (!value) return null;
        const timestamp = new Date(value).getTime();
        return Number.isFinite(timestamp) ? timestamp : null;
    }

    const executionStatusLabels = Object.freeze({
        SUCCESS: "成功",
        FAILED: "失败",
        TIMEOUT: "超时",
        SKIPPED: "跳过",
        RUNNING: "运行中"
    });

    /** 以短暂提示反馈写操作结果。 */
    function showToast(message, isError = false) {
        clearTimeout(toastTimer);
        toast.textContent = message;
        toast.classList.toggle("error", isError);
        toast.classList.add("visible");
        toastTimer = setTimeout(() => toast.classList.remove("visible"), 2600);
    }

    /**
     * 打开自定义确认弹窗。
     * @param {{title:string, message:string, confirmLabel?:string, tone?:'danger'|'run'|'warn', eyebrow?:string}} options
     */
    function openConfirm(options) {
        return new Promise(resolve => {
            confirmResolver = resolve;
            const tone = options.tone || "warn";
            document.querySelector("#confirm-eyebrow").textContent = options.eyebrow || "请确认";
            document.querySelector("#confirm-title").textContent = options.title;
            document.querySelector("#confirm-message").textContent = options.message;
            const okButton = document.querySelector("#confirm-ok");
            okButton.textContent = options.confirmLabel || "确认";
            okButton.className = `confirm-ok confirm-ok-${tone}`;
            const icon = document.querySelector("#confirm-icon");
            icon.className = `confirm-icon confirm-icon-${tone}`;
            icon.textContent = tone === "danger" ? "!" : tone === "run" ? "▶" : "?";
            confirmDialog.showModal();
            okButton.focus();
        });
    }

    function closeConfirm(result) {
        if (confirmDialog.open) confirmDialog.close();
        if (confirmResolver) {
            const resolve = confirmResolver;
            confirmResolver = null;
            resolve(result);
        }
    }

    document.querySelector("#confirm-cancel").addEventListener("click", () => closeConfirm(false));
    document.querySelector("#confirm-ok").addEventListener("click", () => closeConfirm(true));
    confirmDialog.addEventListener("cancel", event => {
        event.preventDefault();
        closeConfirm(false);
    });
    confirmDialog.addEventListener("click", event => {
        if (event.target === confirmDialog) closeConfirm(false);
    });

    if (page === "jobs") {
        initJobsPage();
    } else if (page === "executors") {
        initExecutorsPage();
    } else if (page === "logs") {
        initLogsPage();
    }

    /** 任务管理页交互。 */
    function initJobsPage() {
        const jobsBody = document.querySelector("#jobs-body");
        const emptyState = document.querySelector("#empty-state");
        const searchInput = document.querySelector("#job-search");
        const jobDialog = document.querySelector("#job-dialog");
        const logsDialog = document.querySelector("#logs-dialog");
        const nextTriggersDialog = document.querySelector("#next-triggers-dialog");
        const jobForm = document.querySelector("#job-form");
        let jobs = [];
        let executors = [];
        const routeStrategyLabels = {
            FIRST: "首个节点",
            FAILOVER: "故障转移",
            ROUND_ROBIN: "轮询",
            RANDOM: "随机",
            CONSISTENT_HASH: "一致性哈希",
            BROADCAST: "广播"
        };
        const blockStrategyLabels = {
            SERIAL: "串行跳过",
            DISCARD_LATER: "丢弃后续",
            COVER_EARLY: "覆盖前次"
        };

        function scheduleDescription(job) {
            return job.scheduleType === "CRON" ? `Cron · ${job.cron || "未配置"}` : `固定间隔 · ${job.fixedRateMillis || 0} ms`;
        }

        function executorName(executorId) {
            const executor = executors.find(item => item.id === Number(executorId));
            if (!executor) return executorId != null ? `#${executorId}` : "—";
            return executor.executorName || executor.executorGroup || `#${executor.id}`;
        }

        function strategyLabel(job) {
            const route = routeStrategyLabels[job.routeStrategy] || job.routeStrategy;
            const block = blockStrategyLabels[job.blockStrategy] || job.blockStrategy;
            return `${route} · ${block}`;
        }

        function filteredJobs() {
            const keyword = searchInput.value.trim().toLowerCase();
            if (!keyword) return jobs;
            return jobs.filter(job => [job.name, job.handler, executorName(job.executorId), job.executorId, job.id]
                .some(value => String(value || "").toLowerCase().includes(keyword)));
        }

        function renderJobs() {
            const visibleJobs = filteredJobs();
            jobsBody.innerHTML = visibleJobs.map(job => `
                <tr>
                    <td><span class="job-name" title="${escapeHtml(job.name)}">${escapeHtml(job.name)}</span><span class="job-id">#${escapeHtml(job.id)}</span></td>
                    <td><strong>${escapeHtml(job.handler)}</strong><span class="cell-detail">执行器：${escapeHtml(executorName(job.executorId))}</span></td>
                    <td><span class="schedule-text">${escapeHtml(scheduleDescription(job))}</span><span class="cell-detail">${escapeHtml(strategyLabel(job))}</span></td>
                    <td><span class="schedule-text">${formatTime(job.nextTriggerAt)}</span></td>
                    <td><span class="status ${job.status === "DISABLED" ? "disabled" : "online"}">${job.status === "ENABLED" ? "已启用" : "已暂停"}</span></td>
                    <td><div class="row-actions">
                        <button type="button" class="action-btn action-run" data-action="trigger" data-id="${escapeHtml(job.id)}">执行</button>
                        <button type="button" class="action-btn ${job.status === "ENABLED" ? "action-pause" : "action-enable"}" data-action="toggle" data-id="${escapeHtml(job.id)}">${job.status === "ENABLED" ? "暂停" : "启用"}</button>
                        <button type="button" class="action-btn action-edit" data-action="edit" data-id="${escapeHtml(job.id)}">编辑</button>
                        <button type="button" class="action-btn action-next" data-action="next" data-id="${escapeHtml(job.id)}">下次</button>
                        <button type="button" class="action-btn action-logs" data-action="logs" data-id="${escapeHtml(job.id)}">日志</button>
                        <button type="button" class="action-btn action-delete" data-action="delete" data-id="${escapeHtml(job.id)}">删除</button>
                    </div></td>
                </tr>`).join("");
            emptyState.hidden = visibleJobs.length > 0;
            emptyState.textContent = jobs.length ? "没有匹配的任务" : "尚未创建任务";
            document.querySelector("#job-count-label").textContent = `共 ${jobs.length} 个任务${visibleJobs.length === jobs.length ? "" : `，显示 ${visibleJobs.length} 个`}`;
        }

        async function loadJobs() {
            jobs = await request(SchedulePaths.jobs);
            renderJobs();
        }

        async function loadExecutors() {
            executors = await request(SchedulePaths.executors);
            const online = executors.filter(executor => isExecutorOnline(executor)).length;
            document.querySelectorAll(".metric strong")[3].textContent = String(online);
        }

        function updateMetrics() {
            const metrics = document.querySelectorAll(".metric strong");
            metrics[0].textContent = jobs.length;
            metrics[1].textContent = jobs.filter(job => job.status === "ENABLED").length;
            metrics[2].textContent = jobs.filter(job => job.status === "DISABLED").length;
        }

        function populateExecutorSelect(selectedId) {
            const select = document.querySelector("#executor-id");
            select.innerHTML = `<option value="">请选择执行器</option>${executors.filter(executor => executor.status === "ENABLED").map(executor => `<option value="${escapeHtml(executor.id)}">#${escapeHtml(executor.id)} · ${escapeHtml(executor.executorName)}（${escapeHtml(executor.executorGroup)}）</option>`).join("")}`;
            select.value = selectedId || "";
        }

        async function openJobDialog(job) {
            await loadExecutors();
            jobForm.reset();
            document.querySelector("#job-id").value = job?.id || "";
            document.querySelector("#form-eyebrow").textContent = job ? "编辑" : "新建";
            document.querySelector("#dialog-title").textContent = job ? "编辑任务" : "新建任务";
            document.querySelector("#submit-job-button").textContent = job ? "保存修改" : "创建任务";
            document.querySelector("#form-error").textContent = "";
            if (job) {
                ["name", "handler", "scheduleType", "cron", "fixedRateMillis", "routeStrategy", "blockStrategy", "maxRetryCount", "timeoutSeconds", "parameters"].forEach(key => {
                    const input = jobForm.elements.namedItem(key);
                    if (input && job[key] != null) input.value = job[key];
                });
            }
            populateExecutorSelect(job?.executorId);
            toggleScheduleFields();
            jobDialog.showModal();
        }

        function toggleScheduleFields() {
            const type = document.querySelector("#schedule-type").value;
            document.querySelectorAll(".schedule-field").forEach(field => {
                const visible = field.dataset.schedule === type;
                field.hidden = !visible;
                field.querySelectorAll("input").forEach(input => {
                    input.disabled = !visible;
                    input.required = visible && (input.id === "cron" || input.id === "fixed-rate-millis");
                });
            });
        }

        function jobPayload() {
            const form = new FormData(jobForm);
            const scheduleType = form.get("scheduleType");
            const editingId = document.querySelector("#job-id").value;
            const existing = editingId ? jobs.find(item => item.id === Number(editingId)) : null;
            return {
                name: form.get("name").trim(),
                handler: form.get("handler").trim(),
                executorId: Number(form.get("executorId")),
                scheduleType,
                parameters: form.get("parameters").trim(),
                cron: scheduleType === "CRON" ? form.get("cron").trim() : null,
                fixedRateMillis: scheduleType === "FIXED_RATE" ? Number(form.get("fixedRateMillis")) : null,
                // 保存只落配置，不自动启动；启停仅通过「启用 / 暂停」按钮切换。
                status: existing?.status || "DISABLED",
                routeStrategy: form.get("routeStrategy"),
                blockStrategy: form.get("blockStrategy"),
                maxRetryCount: Number(form.get("maxRetryCount")),
                retryIntervalMillis: 1000,
                timeoutSeconds: Number(form.get("timeoutSeconds"))
            };
        }

        async function openLogs(job) {
            document.querySelector("#logs-title").textContent = `${job.name} · 执行日志`;
            const content = document.querySelector("#logs-content");
            content.innerHTML = "<p class=\"muted\">正在加载日志…</p>";
            logsDialog.showModal();
            try {
                const logs = await request(`${SchedulePaths.logs(job.id)}?limit=100`);
                content.innerHTML = logs.length ? logs.map(log => `
                    <article class="log-item">
                        <div class="log-status ${escapeHtml(log.status)}">${escapeHtml(executionStatusLabels[log.status] || log.status)}</div>
                        <div>
                            <p class="log-message">${escapeHtml(log.message || "无返回信息")}</p>
                            <span class="log-meta">执行器：${log.executorId != null ? "#" + escapeHtml(log.executorId) : "—"}　重试：${escapeHtml(log.retryCount)}</span>
                            <span class="log-meta">触发：${formatTime(log.triggerTime)}　结束：${formatTime(log.finishTime)}</span>
                            <span class="log-meta">目标：${escapeHtml(log.targetAddress || "—")}　耗时：${formatDuration(log.durationMillis)}</span>
                        </div>
                    </article>`).join("") : "<p class=\"log-empty\">暂无执行日志</p>";
            } catch (error) {
                content.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
            }
        }

        function renderNextTriggers(times) {
            const list = document.querySelector("#next-triggers-list");
            list.innerHTML = times.length
                ? times.map((time, index) => `
                    <li>
                        <span class="next-triggers-index">${index + 1}</span>
                        <span class="next-triggers-time">${formatTime(time)}</span>
                    </li>`).join("")
                : "<li class=\"muted\">暂无调度时间</li>";
        }

        async function openNextTriggers(job) {
            document.querySelector("#next-triggers-title").textContent = `${job.name} · 下 10 次调度`;
            document.querySelector("#next-triggers-subtitle").textContent = scheduleDescription(job);
            document.querySelector("#next-triggers-list").innerHTML = "<li class=\"muted\">正在计算…</li>";
            nextTriggersDialog.showModal();
            try {
                const result = await request(`${SchedulePaths.nextTriggers(job.id)}?count=10`);
                renderNextTriggers(result.times || []);
            } catch (error) {
                document.querySelector("#next-triggers-list").innerHTML = `<li class="muted">${escapeHtml(error.message)}</li>`;
            }
        }

        async function previewCronNextTriggers() {
            const cron = document.querySelector("#cron").value.trim();
            if (!cron) {
                showToast("请先填写 Cron 表达式", true);
                return;
            }
            document.querySelector("#next-triggers-title").textContent = "Cron · 下 10 次调度";
            document.querySelector("#next-triggers-subtitle").textContent = `Cron · ${cron}`;
            document.querySelector("#next-triggers-list").innerHTML = "<li class=\"muted\">正在计算…</li>";
            nextTriggersDialog.showModal();
            try {
                const result = await request(SchedulePaths.nextTriggersPreview, {
                    method: "POST",
                    body: JSON.stringify({ scheduleType: "CRON", cron, count: 10 })
                });
                renderNextTriggers(result.times || []);
            } catch (error) {
                document.querySelector("#next-triggers-list").innerHTML = `<li class="muted">${escapeHtml(error.message)}</li>`;
            }
        }

        jobsBody.addEventListener("click", async event => {
            const button = event.target.closest("button[data-action]");
            if (!button) return;
            const job = jobs.find(item => item.id === Number(button.dataset.id));
            if (!job) return;
            try {
                if (button.dataset.action === "edit") return await openJobDialog(job);
                if (button.dataset.action === "logs") return openLogs(job);
                if (button.dataset.action === "next") return openNextTriggers(job);
                if (button.dataset.action === "delete") {
                    const confirmed = await openConfirm({
                        tone: "danger",
                        eyebrow: "删除任务",
                        title: "确认删除该任务？",
                        message: `将永久删除任务「${job.name}」（#${job.id}），此操作不可恢复。`,
                        confirmLabel: "确认删除"
                    });
                    if (!confirmed) return;
                    await request(SchedulePaths.job(job.id), { method: "DELETE" });
                    showToast("任务已删除");
                } else if (button.dataset.action === "trigger") {
                    const confirmed = await openConfirm({
                        tone: "run",
                        eyebrow: "立即执行",
                        title: "确认立即执行一次？",
                        message: `将立即触发任务「${job.name}」（#${job.id}），不会修改原有定时计划。`,
                        confirmLabel: "立即执行"
                    });
                    if (!confirmed) return;
                    await request(SchedulePaths.trigger(job.id), { method: "POST", body: "{}" });
                    showToast("任务已提交执行");
                } else if (button.dataset.action === "toggle") {
                    await request(SchedulePaths.status(job.id), {
                        method: "POST",
                        body: JSON.stringify({ status: job.status === "ENABLED" ? "DISABLED" : "ENABLED" })
                    });
                    showToast(job.status === "ENABLED" ? "任务已暂停" : "任务已启用");
                }
                await loadJobs();
                updateMetrics();
            } catch (error) { showToast(error.message, true); }
        });

        jobForm.addEventListener("submit", async event => {
            event.preventDefault();
            const id = document.querySelector("#job-id").value;
            const submitButton = document.querySelector("#submit-job-button");
            const formError = document.querySelector("#form-error");
            submitButton.disabled = true;
            formError.textContent = "";
            try {
                await request(id ? SchedulePaths.job(id) : SchedulePaths.jobs, {
                    method: id ? "PUT" : "POST",
                    body: JSON.stringify(jobPayload())
                });
                jobDialog.close();
                await loadJobs();
                updateMetrics();
                showToast(id ? "任务已更新" : "任务已创建");
            } catch (error) { formError.textContent = error.message; } finally { submitButton.disabled = false; }
        });

        document.querySelector("#create-job-button").addEventListener("click", () => openJobDialog().catch(error => showToast(error.message, true)));
        document.querySelector("#refresh-button").addEventListener("click", () => Promise.all([loadJobs(), loadExecutors()]).then(() => { renderJobs(); updateMetrics(); showToast("任务列表已刷新"); }).catch(error => showToast(error.message, true)));
        searchInput.addEventListener("input", renderJobs);
        document.querySelector("#schedule-type").addEventListener("change", toggleScheduleFields);
        document.querySelector("#preview-cron-next").addEventListener("click", () => previewCronNextTriggers());
        document.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", () => jobDialog.close()));
        document.querySelector("[data-close-logs]").addEventListener("click", () => logsDialog.close());
        document.querySelector("[data-close-next-triggers]").addEventListener("click", () => nextTriggersDialog.close());

        Promise.all([loadJobs(), loadExecutors()]).then(() => { renderJobs(); updateMetrics(); }).catch(error => showToast(error.message, true));
    }

    /** 执行器管理页交互。 */
    function initExecutorsPage() {
        const executorDialog = document.querySelector("#executor-dialog");
        const executorForm = document.querySelector("#executor-form");
        let executors = [];
        let editingExecutorNodeId = null;

        async function loadExecutors() {
            executors = await request(SchedulePaths.executors);
            const container = document.querySelector("#executors-list");
            const now = Date.now();
            const online = executors.filter(executor => isExecutorOnline(executor, now));
            container.innerHTML = executors.length ? executors.map(executor => {
                const onlineNow = isExecutorOnline(executor, now);
                const disabled = executor.status === "DISABLED";
                const presenceClass = disabled ? "disabled" : (onlineNow ? "online" : "offline");
                const presenceLabel = disabled ? "已禁用" : (onlineNow ? "在线" : "离线");
                const heartbeatHint = disabled
                    ? "调度已禁用"
                    : (onlineNow ? "心跳正常" : "心跳超时，不可调度");
                return `
                <article class="executor-card ${presenceClass}">
                    <div class="executor-card-header">
                        <strong>${escapeHtml(executor.executorName)}</strong>
                        <span class="status ${presenceClass}">${presenceLabel}</span>
                    </div>
                    <span>ID：#${escapeHtml(executor.id)}　分组：${escapeHtml(executor.executorGroup)}</span>
                    <span>${executor.addressMode === "MANUAL" ? "手动地址" : "自动注册"}：${escapeHtml(formatExecutorAddresses(executor.address))}</span>
                    <span>最后心跳：${formatTime(executor.lastHeartbeatTime)}（${heartbeatHint}）</span>
                    <div class="executor-actions">
                        <button type="button" class="action-btn action-edit" data-executor-action="edit" data-id="${escapeHtml(executor.id)}">编辑</button>
                        <button type="button" class="action-btn ${executor.status === "ENABLED" ? "action-pause" : "action-enable"}" data-executor-action="toggle" data-id="${escapeHtml(executor.id)}">${executor.status === "ENABLED" ? "禁用" : "启用"}</button>
                        <button type="button" class="action-btn action-delete" data-executor-action="delete" data-id="${escapeHtml(executor.id)}">删除</button>
                    </div>
                </article>`;
            }).join("") : "<p class=\"log-empty\">尚未登记执行器，请先新增节点或启动执行器实例。</p>";
            const metrics = document.querySelectorAll(".metric strong");
            metrics[0].textContent = String(executors.length);
            metrics[1].textContent = String(executors.filter(executor => executor.status === "ENABLED").length);
            metrics[2].textContent = String(online.length);
        }

        function openExecutorDialog(executor) {
            executorForm.reset();
            editingExecutorNodeId = executor?.id || null;
            const groupInput = document.querySelector("#executor-group");
            groupInput.value = executor?.executorGroup || "";
            groupInput.readOnly = Boolean(executor);
            document.querySelector("#executor-name").value = executor?.executorName || "";
            document.querySelector("#executor-node-address").value = formatAddressTextarea(executor?.address);
            document.querySelector("#executor-address-mode").value = executor?.addressMode || "AUTO_REGISTER";
            document.querySelector("#executor-node-status").value = executor?.status || "ENABLED";
            document.querySelector("#executor-form-eyebrow").textContent = executor ? "编辑" : "新增";
            document.querySelector("#executor-dialog-title").textContent = executor ? "编辑执行器" : "新增执行器";
            document.querySelector("#submit-executor-button").textContent = executor ? "保存修改" : "创建执行器";
            document.querySelector("#executor-form-error").textContent = "";
            toggleExecutorAddressField();
            executorDialog.showModal();
        }

        function formatAddressTextarea(address) {
            return String(address || "")
                .split(/[,;\n\r]+/)
                .map(item => item.trim())
                .filter(Boolean)
                .join("\n");
        }

        function formatExecutorAddresses(address) {
            const list = String(address || "")
                .split(/[,;\n\r]+/)
                .map(item => item.trim())
                .filter(Boolean);
            return list.length ? list.join("、") : "等待心跳上报";
        }

        function toggleExecutorAddressField() {
            const isManual = document.querySelector("#executor-address-mode").value === "MANUAL";
            const input = document.querySelector("#executor-node-address");
            const hint = document.querySelector("#executor-address-hint");
            input.readOnly = !isManual;
            input.required = isManual;
            input.placeholder = isManual
                ? "每行一个，或用逗号分隔\n例如：\nhttp://10.0.0.1:18081\nhttp://10.0.0.2:18081"
                : "由执行器心跳自动维护，节点下线或心跳超时会自动剔除";
            if (hint) {
                hint.textContent = isManual
                    ? "手动模式：可配置多个固定地址，路由策略会在这些地址间选择。"
                    : "自动注册：地址由各实例心跳上报；优雅下线或心跳超时后会自动从列表剔除。";
            }
        }

        document.querySelector("#executors-list").addEventListener("click", async event => {
            const button = event.target.closest("button[data-executor-action]");
            if (!button) return;
            const executor = executors.find(item => item.id === Number(button.dataset.id));
            if (!executor) return;
            try {
                if (button.dataset.executorAction === "edit") return openExecutorDialog(executor);
                if (button.dataset.executorAction === "delete") {
                    const confirmed = await openConfirm({
                        tone: "danger",
                        eyebrow: "删除执行器",
                        title: "确认删除该执行器？",
                        message: `将删除「${executor.executorName}」（${executor.executorGroup}）。同分组实例下次心跳可自动重新注册。`,
                        confirmLabel: "确认删除"
                    });
                    if (!confirmed) return;
                    await request(SchedulePaths.executor(executor.id), { method: "DELETE" });
                    showToast("执行器已删除");
                } else if (button.dataset.executorAction === "toggle") {
                    const isEnabled = executor.status === "ENABLED";
                    const confirmed = await openConfirm({
                        tone: isEnabled ? "warn" : "run",
                        eyebrow: isEnabled ? "禁用执行器" : "启用执行器",
                        title: isEnabled ? "确认禁用该执行器？" : "确认重新启用？",
                        message: isEnabled
                            ? `「${executor.executorName}」禁用后将不再接收新的调度任务（与进程离线不同）。`
                            : `「${executor.executorName}」启用后可重新参与任务路由。`,
                        confirmLabel: isEnabled ? "确认禁用" : "确认启用"
                    });
                    if (!confirmed) return;
                    await request(SchedulePaths.executorStatus(executor.id), {
                        method: "POST",
                        body: JSON.stringify({ status: isEnabled ? "DISABLED" : "ENABLED" })
                    });
                    showToast(isEnabled ? "执行器已禁用，不再接收任务" : "执行器已重新启用");
                }
                await loadExecutors();
            } catch (error) { showToast(error.message, true); }
        });

        executorForm.addEventListener("submit", async event => {
            event.preventDefault();
            const submitButton = document.querySelector("#submit-executor-button");
            const formError = document.querySelector("#executor-form-error");
            const payload = {
                executorGroup: document.querySelector("#executor-group").value.trim(),
                executorName: document.querySelector("#executor-name").value.trim(),
                address: document.querySelector("#executor-node-address").value.trim() || null,
                addressMode: document.querySelector("#executor-address-mode").value,
                status: document.querySelector("#executor-node-status").value
            };
            submitButton.disabled = true;
            formError.textContent = "";
            try {
                await request(editingExecutorNodeId ? SchedulePaths.executor(editingExecutorNodeId) : SchedulePaths.executors, {
                    method: editingExecutorNodeId ? "PUT" : "POST",
                    body: JSON.stringify(payload)
                });
                executorDialog.close();
                await loadExecutors();
                showToast(editingExecutorNodeId ? "执行器已更新" : "执行器已创建");
            } catch (error) { formError.textContent = error.message; } finally { submitButton.disabled = false; }
        });

        document.querySelector("#create-executor-button").addEventListener("click", () => openExecutorDialog());
        document.querySelector("#refresh-executors-button").addEventListener("click", () => loadExecutors().then(() => showToast("执行器列表已刷新")).catch(error => showToast(error.message, true)));
        document.querySelector("#executor-address-mode").addEventListener("change", toggleExecutorAddressField);
        document.querySelectorAll("[data-close-executor-dialog]").forEach(button => button.addEventListener("click", () => executorDialog.close()));

        loadExecutors().catch(error => showToast(error.message, true));
    }

    /** 执行日志查询页交互。 */
    function initLogsPage() {
        const resultBox = document.querySelector("#log-query-results");
        const resultCount = document.querySelector("#log-result-count");
        const summary = document.querySelector("#log-summary");
        let jobs = [];
        let executors = [];

        function setActiveChip(range) {
            document.querySelectorAll(".log-chip").forEach(chip => {
                chip.classList.toggle("active", range != null && chip.dataset.range === range);
            });
        }

        function applyTimeRange(rangeKey) {
            const now = new Date();
            const ranges = {
                "1h": 60 * 60 * 1000,
                "24h": 24 * 60 * 60 * 1000,
                "7d": 7 * 24 * 60 * 60 * 1000,
                "30d": 30 * 24 * 60 * 60 * 1000
            };
            const span = ranges[rangeKey] || ranges["24h"];
            document.querySelector("#log-from").value = toDateTimeLocalValue(new Date(now.getTime() - span));
            document.querySelector("#log-to").value = toDateTimeLocalValue(now);
            setActiveChip(rangeKey);
        }

        function applyDefaultTimeRange() {
            applyTimeRange("24h");
            document.querySelector("#log-job-id").value = "";
            document.querySelector("#log-executor-id").value = "";
            document.querySelector("#log-status").value = "";
            document.querySelector("#log-limit").value = "100";
        }

        function populateFilters() {
            const jobSelect = document.querySelector("#log-job-id");
            const executorSelect = document.querySelector("#log-executor-id");
            const selectedJob = jobSelect.value;
            const selectedExecutor = executorSelect.value;
            jobSelect.innerHTML = `<option value="">全部任务</option>${jobs.map(job =>
                `<option value="${escapeHtml(job.id)}">#${escapeHtml(job.id)} · ${escapeHtml(job.name)}</option>`
            ).join("")}`;
            executorSelect.innerHTML = `<option value="">全部执行器</option>${executors.map(executor =>
                `<option value="${escapeHtml(executor.id)}">#${escapeHtml(executor.id)} · ${escapeHtml(executor.executorName)}</option>`
            ).join("")}`;
            jobSelect.value = selectedJob;
            executorSelect.value = selectedExecutor;
        }

        function jobLabel(jobId) {
            const job = jobs.find(item => item.id === Number(jobId));
            return job ? `${job.name}（#${job.id}）` : (jobId != null ? `#${jobId}` : "—");
        }

        function executorLabel(executorId) {
            const executor = executors.find(item => item.id === Number(executorId));
            if (!executor) return executorId != null ? `#${executorId}` : "—";
            return executor.executorName || `#${executor.id}`;
        }

        function buildQueryPath() {
            const params = new URLSearchParams();
            const jobId = document.querySelector("#log-job-id").value;
            const executorId = document.querySelector("#log-executor-id").value;
            const status = document.querySelector("#log-status").value;
            const from = fromDateTimeLocalValue(document.querySelector("#log-from").value);
            const to = fromDateTimeLocalValue(document.querySelector("#log-to").value);
            const limit = document.querySelector("#log-limit").value || "100";
            if (jobId) params.set("jobId", jobId);
            if (executorId) params.set("executorId", executorId);
            if (status) params.set("status", status);
            if (from != null) params.set("from", String(from));
            if (to != null) params.set("to", String(to));
            params.set("limit", limit);
            return `${SchedulePaths.queryLogs}?${params.toString()}`;
        }

        function renderSummary(logs) {
            const success = logs.filter(log => log.status === "SUCCESS").length;
            const failed = logs.filter(log => log.status === "FAILED" || log.status === "TIMEOUT").length;
            const other = logs.length - success - failed;
            document.querySelector("#log-stat-total").textContent = String(logs.length);
            document.querySelector("#log-stat-success").textContent = String(success);
            document.querySelector("#log-stat-failed").textContent = String(failed);
            document.querySelector("#log-stat-other").textContent = String(Math.max(other, 0));
            summary.hidden = false;
        }

        function renderLogs(logs) {
            resultCount.textContent = logs.length ? `共 ${logs.length} 条结果` : "无匹配日志";
            renderSummary(logs);
            resultBox.innerHTML = logs.length ? logs.map(log => `
                <article class="log-item">
                    <div class="log-status ${escapeHtml(log.status)}">${escapeHtml(executionStatusLabels[log.status] || log.status)}</div>
                    <div>
                        <p class="log-message">${escapeHtml(log.message || "无返回信息")}</p>
                        <span class="log-meta">任务：${escapeHtml(jobLabel(log.jobId))}　执行器：${escapeHtml(executorLabel(log.executorId))}　重试：${escapeHtml(log.retryCount)}</span>
                        <span class="log-meta">触发：${formatTime(log.triggerTime)}　结束：${formatTime(log.finishTime)}</span>
                        <span class="log-meta">目标：${escapeHtml(log.targetAddress || "—")}　耗时：${formatDuration(log.durationMillis)}</span>
                    </div>
                </article>`).join("") : "<p class=\"log-empty\">没有符合条件的执行日志</p>";
        }

        async function queryLogs() {
            const from = fromDateTimeLocalValue(document.querySelector("#log-from").value);
            const to = fromDateTimeLocalValue(document.querySelector("#log-to").value);
            if (from != null && to != null && from > to) {
                showToast("开始时间不能晚于结束时间", true);
                return;
            }
            resultBox.innerHTML = "<p class=\"log-empty\">正在查询…</p>";
            resultCount.textContent = "查询中";
            summary.hidden = true;
            try {
                const logs = await request(buildQueryPath());
                renderLogs(logs);
            } catch (error) {
                resultCount.textContent = "查询失败";
                resultBox.innerHTML = `<p class="log-empty">${escapeHtml(error.message)}</p>`;
                showToast(error.message, true);
            }
        }

        async function bootstrap() {
            applyDefaultTimeRange();
            const [jobList, executorList] = await Promise.all([
                request(SchedulePaths.jobs),
                request(SchedulePaths.executors)
            ]);
            jobs = jobList;
            executors = executorList;
            populateFilters();
            await queryLogs();
        }

        const runQuery = () => queryLogs();
        const resetFilters = () => {
            applyDefaultTimeRange();
            populateFilters();
            queryLogs();
        };
        document.querySelector("#query-logs-button").addEventListener("click", runQuery);
        document.querySelector("#reset-log-filters-button").addEventListener("click", resetFilters);
        document.querySelector("#reset-log-filters-button-bottom").addEventListener("click", resetFilters);
        document.querySelector("#log-query-form").addEventListener("submit", event => {
            event.preventDefault();
            queryLogs();
        });
        document.querySelector(".log-quick-ranges").addEventListener("click", event => {
            const chip = event.target.closest(".log-chip");
            if (!chip) return;
            applyTimeRange(chip.dataset.range);
            queryLogs();
        });
        ["#log-from", "#log-to"].forEach(selector => {
            document.querySelector(selector).addEventListener("change", () => setActiveChip(null));
        });

        bootstrap().catch(error => showToast(error.message, true));
    }
})();
