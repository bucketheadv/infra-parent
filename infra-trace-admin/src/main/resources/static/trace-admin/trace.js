(function () {
    "use strict";

    var PAGE = document.body.dataset.page;
    var toastEl = document.getElementById("toast");

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function showToast(message, isError) {
        toastEl.textContent = message;
        toastEl.classList.toggle("error", !!isError);
        toastEl.classList.add("show");
        setTimeout(function () { toastEl.classList.remove("show"); }, 2400);
    }

    function formatTime(millis) {
        if (!millis) return "-";
        var d = new Date(millis);
        var pad = function (n) { return n < 10 ? "0" + n : String(n); };
        return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
            " " + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
    }

    function formatDuration(millis) {
        if (millis === undefined || millis === null) return "-";
        if (millis < 1000) return millis.toFixed(0) + " ms";
        return (millis / 1000).toFixed(2) + " s";
    }

    function statusBadge(success) {
        return '<span class="status ' + (success ? "ok" : "err") + '">' +
            (success ? "成功" : "异常") + "</span>";
    }

    function fetchJson(url) {
        return fetch(url, { headers: { "Accept": "application/json" } }).then(function (response) {
            if (!response.ok) throw new Error("HTTP " + response.status);
            return response.json();
        });
    }

    /* ── 链路列表页 ─────────────────────────────────────────── */
    if (PAGE === "traces") {
        var tracesBody = document.getElementById("traces-body");
        var resultLabel = document.getElementById("result-label");
        var emptyState = document.getElementById("empty");
        var fromDate = document.getElementById("filter-from-date");
        var fromTime = document.getElementById("filter-from-time");
        var toDate = document.getElementById("filter-to-date");
        var toTime = document.getElementById("filter-to-time");
        var keywordInput = document.getElementById("filter-keyword");

        function pad2(n) { return n < 10 ? "0" + n : String(n); }
        function toDateStr(d) { return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()); }
        function toTimeStr(d) { return pad2(d.getHours()) + ":" + pad2(d.getMinutes()) + ":" + pad2(d.getSeconds()); }

        function setDefaultRange(minutes) {
            if (minutes > 0) {
                var now = new Date();
                var from = new Date(now.getTime() - minutes * 60 * 1000);
                fromDate.value = toDateStr(from);
                fromTime.value = toTimeStr(from);
                toDate.value = "";
                toTime.value = "00:00:00";
            } else {
                fromDate.value = "";
                fromTime.value = "00:00:00";
                toDate.value = "";
                toTime.value = "00:00:00";
            }
        }
        setDefaultRange(30);

        document.querySelectorAll(".log-chip").forEach(function (chip) {
            chip.addEventListener("click", function () {
                document.querySelectorAll(".log-chip").forEach(function (c) { c.classList.remove("active"); });
                chip.classList.add("active");
                setDefaultRange(parseInt(chip.dataset.minutes, 10));
                loadTraces();
            });
        });

        function toMillis(dateStr, timeStr) {
            if (!dateStr) return null;
            var full = dateStr + "T" + (timeStr || "00:00:00");
            var millis = new Date(full).getTime();
            return isNaN(millis) ? null : millis;
        }

        function renderTraces(traces) {
            tracesBody.innerHTML = "";
            if (!traces || !traces.length) {
                resultLabel.textContent = "时间范围内暂无数据";
                emptyState.hidden = false;
                return;
            }
            emptyState.hidden = true;
            resultLabel.textContent = "共 " + traces.length + " 条链路";
            var spanCount = 0;
            var serviceSet = {};
            var failedCount = 0;
            traces.forEach(function (trace) {
                spanCount += trace.spanCount || 0;
                (trace.serviceNames || []).forEach(function (name) { serviceSet[name] = true; });
                if (!trace.success) failedCount++;
                var services = (trace.serviceNames || [])
                    .map(function (name) { return '<span class="tag">' + escapeHtml(name) + "</span>"; })
                    .join("");
                var uid = (trace.uids || []).join(", ") || "-";
                var tr = document.createElement("tr");
                tr.innerHTML =
                    '<td class="trace-id-cell"><a class="link" href="/traces/' + encodeURIComponent(trace.traceId) + '">' + escapeHtml(trace.traceId) + "</a></td>" +
                    '<td class="time-cell">' + escapeHtml(formatTime(trace.startTimeMillis)) + "</td>" +
                    "<td>" + escapeHtml(formatDuration(trace.durationMillis)) + "</td>" +
                    '<td><span class="service-tags">' + services + "</span></td>" +
                    "<td>" + (trace.spanCount || 0) + "</td>" +
                    "<td>" + uid + "</td>" +
                    "<td>" + statusBadge(trace.success) + "</td>" +
                    '<td class="actions-cell"><a class="link" href="/traces/' + encodeURIComponent(trace.traceId) + '">查看</a></td>';
                tracesBody.appendChild(tr);
            });
            document.getElementById("metric-traces").textContent = traces.length;
            document.getElementById("metric-spans").textContent = spanCount;
            document.getElementById("metric-services").textContent = Object.keys(serviceSet).length;
            document.getElementById("metric-failed").textContent = failedCount;
        }

        function buildQuery() {
            var params = new URLSearchParams();
            var from = toMillis(fromDate.value, fromTime.value);
            var to = toMillis(toDate.value, toTime.value);
            if (from !== null) params.set("fromMillis", String(from));
            if (to !== null) params.set("toMillis", String(to));
            var kw = keywordInput.value.trim();
            if (kw) params.set("keyword", kw);
            return params.toString();
        }

        function loadTraces() {
            var qs = buildQuery();
            fetchJson("/api/trace/traces?limit=200&" + qs)
                .then(renderTraces)
                .catch(function (error) {
                    showToast("加载失败：" + error.message, true);
                });
        }

        document.getElementById("apply-filter").addEventListener("click", loadTraces);
        document.getElementById("reset-filter").addEventListener("click", function () {
            document.querySelectorAll(".log-chip").forEach(function (c) { c.classList.remove("active"); });
            fromDate.value = "";
            fromTime.value = "00:00:00";
            toDate.value = "";
            toTime.value = "00:00:00";
            keywordInput.value = "";
            loadTraces();
        });
        document.getElementById("refresh").addEventListener("click", loadTraces);
        loadTraces();
    }

    /* ── 链路详情页 ─────────────────────────────────────────── */
    if (PAGE === "detail") {
        var metaPanel = document.getElementById("meta-panel");
        var waterfallEl = document.getElementById("waterfall");
        var waterfallLabel = document.getElementById("waterfall-label");
        var emptyState = document.getElementById("empty");
        var detailPanel = document.getElementById("span-detail-panel");
        var detailSpanId = document.getElementById("detail-span-id");
        var kvEl = document.getElementById("detail-kv");
        var requestEl = document.getElementById("detail-request");
        var responseEl = document.getElementById("detail-response");
        var headersEl = document.getElementById("detail-headers");
        var headersBlock = document.getElementById("detail-headers-block");
        var errorBlock = document.getElementById("detail-error-block");
        var errorEl = document.getElementById("detail-error");

        function traceIdFromPath() {
            var parts = location.pathname.split("/");
            return decodeURIComponent(parts[parts.length - 1]);
        }

        function buildTree(spans) {
            var childrenByParent = {};
            var roots = [];
            spans.forEach(function (span) {
                var parent = span.parentSpanId;
                if (!parent || !spans.some(function (s) { return s.spanId === parent; })) {
                    roots.push(span);
                } else {
                    (childrenByParent[parent] = childrenByParent[parent] || []).push(span);
                }
            });
            var ordered = [];
            (function walk(nodes) {
                nodes.forEach(function (node) {
                    ordered.push(node);
                    (childrenByParent[node.spanId] || []).forEach(function (child) {
                        walk([child]);
                    });
                });
            })(roots);
            spans.forEach(function (span) {
                if (ordered.indexOf(span) === -1) ordered.push(span);
            });
            return ordered;
        }

        function renderMeta(traceId, spans) {
            metaPanel.hidden = false;
            var ok = spans.every(function (s) { return s.success; });
            document.getElementById("trace-id-label").textContent = traceId;
            document.getElementById("m-trace-id").textContent = traceId;
            var minStart = spans.reduce(function (m, s) { return Math.min(m, s.startTimeMillis); }, Number.MAX_SAFE_INTEGER);
            var maxEnd = spans.reduce(function (m, s) { return Math.max(m, s.startTimeMillis + s.durationMillis); }, 0);
            document.getElementById("m-start").textContent = formatTime(minStart);
            document.getElementById("m-duration").textContent = formatDuration(maxEnd - minStart);
            document.getElementById("m-spans").textContent = spans.length;
            document.getElementById("m-services").textContent =
                spans.map(function (s) { return s.serviceName; }).filter(function (v, i, a) { return a.indexOf(v) === i; }).join(", ");
            var statusEl = document.getElementById("m-status");
            statusEl.innerHTML = statusBadge(ok);
        }

        function renderWaterfall(traceId, spans) {
            if (!spans || !spans.length) {
                waterfallLabel.textContent = "该链路暂无 span";
                emptyState.hidden = false;
                return;
            }
            emptyState.hidden = true;
            waterfallLabel.textContent = spans.length + " 个 span";
            var minStart = spans.reduce(function (m, s) { return Math.min(m, s.startTimeMillis); }, Number.MAX_SAFE_INTEGER);
            var maxEnd = spans.reduce(function (m, s) { return Math.max(m, s.startTimeMillis + s.durationMillis); }, 0);
            var total = Math.max(maxEnd - minStart, 1);

            waterfallEl.innerHTML = "";
            buildTree(spans).forEach(function (span) {
                var left = ((span.startTimeMillis - minStart) / total * 100).toFixed(2);
                var width = Math.max(span.durationMillis / total * 100, 0.4).toFixed(2);
                var row = document.createElement("div");
                row.className = "wf-row";
                row.innerHTML =
                    '<div class="wf-service">' +
                    '<span class="wf-service-name">' + escapeHtml(span.serviceName) + "</span>" +
                    '<span class="wf-operation">' + escapeHtml(span.operation) + "</span>" +
                    '<span class="wf-ids mono">span ' + escapeHtml(span.spanId) +
                    (span.parentSpanId ? " · parent " + escapeHtml(span.parentSpanId) : "") + "</span>" +
                    "</div>" +
                    '<div class="wf-bar-wrap"><span class="wf-bar' + (span.success ? "" : " error") +
                    '" style="left:' + left + "%;width:" + width + '%"></span></div>' +
                    '<span class="wf-duration">' + formatDuration(span.durationMillis) + "</span>";
                row.addEventListener("click", function () {
                    waterfallEl.querySelectorAll(".wf-row").forEach(function (r) { r.classList.remove("selected"); });
                    row.classList.add("selected");
                    showSpanDetail(span);
                });
                waterfallEl.appendChild(row);
            });
        }

        function showSpanDetail(span) {
            detailPanel.hidden = false;
            detailSpanId.textContent = span.spanId;
            kvEl.innerHTML = [
                ["traceId", span.traceId],
                ["spanId", span.spanId],
                ["parentSpanId", span.parentSpanId || "-"],
                ["service", span.serviceName],
                ["operation", span.operation],
                ["method", span.httpMethod || "-"],
                ["start", formatTime(span.startTimeMillis)],
                ["duration", formatDuration(span.durationMillis)],
                ["uid", span.uid || "-"],
                ["status", span.success ? "成功" : "异常"]
            ].map(function (pair) {
                return "<dt>" + escapeHtml(pair[0]) + "</dt><dd>" + escapeHtml(pair[1]) + "</dd>";
            }).join("");
            requestEl.textContent = span.requestBody || "(未采集)";
            responseEl.textContent = span.responseBody || "(未采集)";
            if (span.requestHeaders) {
                headersBlock.hidden = false;
                headersEl.textContent = span.requestHeaders;
            } else {
                headersBlock.hidden = true;
                headersEl.textContent = "";
            }
            if (span.errorMessage || span.errorStackTrace) {
                errorBlock.hidden = false;
                errorEl.textContent = (span.errorMessage ? "message: " + span.errorMessage + "\n\n" : "") +
                    (span.errorStackTrace || "");
            } else {
                errorBlock.hidden = true;
                errorEl.textContent = "";
            }
            detailPanel.scrollIntoView({ behavior: "smooth", block: "start" });
        }

        document.getElementById("close-detail").addEventListener("click", function () {
            detailPanel.hidden = true;
            waterfallEl.querySelectorAll(".wf-row").forEach(function (r) { r.classList.remove("selected"); });
        });

        function loadDetail() {
            var traceId = traceIdFromPath();
            fetchJson("/api/trace/traces/" + encodeURIComponent(traceId))
                .then(function (spans) {
                    renderMeta(traceId, spans);
                    renderWaterfall(traceId, spans);
                })
                .catch(function (error) {
                    waterfallLabel.textContent = "加载失败";
                    showToast("加载链路详情失败：" + error.message, true);
                });
            loadLogs(traceId);
        }

        function loadLogs(traceId) {
            var logsBody = document.getElementById("logs-body");
            var logsLabel = document.getElementById("logs-label");
            var logsEmpty = document.getElementById("logs-empty");
            fetchJson("/api/trace/logs/" + encodeURIComponent(traceId) + "?limit=500")
                .then(function (logs) {
                    logsBody.innerHTML = "";
                    if (!logs || !logs.length) {
                        logsLabel.textContent = "暂无关联日志";
                        logsEmpty.hidden = false;
                        return;
                    }
                    logsEmpty.hidden = true;
                    logsLabel.textContent = "共 " + logs.length + " 条日志";
                    logs.forEach(function (log) {
                        var row = document.createElement("div");
                        row.className = "log-row log-level-" + log.level.toLowerCase();
                        var time = new Date(log.timestamp);
                        var pad = function (n) { return n < 10 ? "0" + n : String(n); };
                        var ts = pad(time.getHours()) + ":" + pad(time.getMinutes()) + ":" + pad(time.getSeconds()) + "." + String(time.getMilliseconds()).padStart(3, "0");
                        row.innerHTML =
                            '<span class="log-time">' + ts + '</span>' +
                            '<span class="log-level">' + log.level + '</span>' +
                            '<span class="log-logger">' + escapeHtml(log.logger) + '</span>' +
                            '<span class="log-msg">' + escapeHtml(log.message) + '</span>';
                        if (log.exception) {
                            var exc = document.createElement("pre");
                            exc.className = "log-exception";
                            exc.textContent = log.exception;
                            row.appendChild(exc);
                        }
                        logsBody.appendChild(row);
                    });
                })
                .catch(function () {
                    logsLabel.textContent = "日志加载失败";
                });
        }

        document.getElementById("refresh").addEventListener("click", loadDetail);
        loadDetail();
    }
})();
