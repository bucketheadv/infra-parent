(function () {
    "use strict";

    const API_ROOT = "/api/rocketmq";
    const tokenHeader = "X-Infra-RocketMQ-Admin-Token";

    function currentPage() {
        return document.body.dataset.page || "";
    }

    function adminHeaders(extra) {
        const headers = Object.assign({ "Content-Type": "application/json" }, extra || {});
        if (document.body.dataset.adminAuthEnabled === "true" && document.body.dataset.adminAccessToken) {
            headers[tokenHeader] = document.body.dataset.adminAccessToken;
        }
        return headers;
    }

    function api(path, options) {
        const opts = options || {};
        const request = {
            method: opts.method || "GET",
            headers: adminHeaders(opts.headers)
        };
        if (opts.body !== undefined) {
            request.body = JSON.stringify(opts.body);
        }
        return fetch(API_ROOT + path, request).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (payload) {
                if (!response.ok) {
                    const error = new Error(payload.message || ("HTTP " + response.status));
                    error.status = response.status;
                    throw error;
                }
                return payload;
            });
        });
    }

    function escapeHtml(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function fmtTime(millis) {
        if (!millis) return "-";
        const d = new Date(millis);
        function pad(n) { return n < 10 ? "0" + n : String(n); }
        return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
            " " + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
    }

    function fmtDateTimeLocal(millis) {
        if (!millis) return "";
        const d = new Date(millis);
        function pad(n) { return n < 10 ? "0" + n : String(n); }
        return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
            "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
    }

    function toast(message, isError) {
        const el = document.getElementById("toast");
        if (!el) return;
        el.textContent = message;
        el.classList.toggle("error", !!isError);
        el.classList.add("visible");
        el.showPopover ? el.showPopover() : null;
        clearTimeout(toast._timer);
        toast._timer = setTimeout(function () {
            el.classList.remove("visible");
            if (el.hidePopover) el.hidePopover();
        }, 3200);
    }

    function permText(perm) {
        if (perm === undefined || perm === null) return "-";
        const bits = [];
        if ((perm & 4) !== 0) bits.push("读");
        if ((perm & 2) !== 0) bits.push("写");
        if ((perm & 1) !== 0) bits.push("继承");
        if ((perm & 8) !== 0) bits.push("优先");
        if (bits.length === 0) return "无权限";
        return bits.join("+");
    }

    function statusPill(text, online) {
        const cls = online ? "online" : "offline";
        return '<span class="status ' + cls + '">' + escapeHtml(text) + "</span>";
    }

    function confirmDialog(message, onOk, okText) {
        const dialog = document.getElementById("confirm-dialog");
        if (!dialog) { if (window.confirm(message)) onOk(); return; }
        const title = document.getElementById("confirm-title");
        const msg = document.getElementById("confirm-message");
        const ok = document.getElementById("confirm-ok");
        const cancel = document.getElementById("confirm-cancel");
        if (title) title.textContent = "确认操作";
        if (msg) msg.textContent = message;
        if (ok) {
            ok.textContent = okText || "确认";
            ok.classList.toggle("danger-button", !!(okText && okText.indexOf("删除") >= 0));
            ok.onclick = function () { closeDialog("confirm-dialog"); onOk(); };
        }
        if (cancel) cancel.onclick = function () { closeDialog("confirm-dialog"); };
        openDialog("confirm-dialog");
    }

    function openDialog(id) {
        const el = document.getElementById(id);
        if (!el) return;
        if (el.showModal) {
            if (!el.open) el.showModal();
            return;
        }
        el.setAttribute("open", "");
        document.body.classList.add("dialog-fallback-open");
    }

    function closeDialog(id) {
        const el = document.getElementById(id);
        if (!el) return;
        if (el.close) el.close();
        else el.removeAttribute("open");
        if (!document.querySelector("dialog[open]")) {
            document.body.classList.remove("dialog-fallback-open");
        }
    }

    function bindCloseButtons() {
        document.querySelectorAll("[data-close-dialog]").forEach(function (button) {
            button.addEventListener("click", function () {
                const dialog = button.closest("dialog");
                if (dialog) closeDialog(dialog.id);
            });
        });
    }

    /* ---------------- 仪表盘 ---------------- */

    function initDashboard() {
        const refresh = document.getElementById("refresh-button");
        const load = function () {
            api("/cluster").then(function (summary) {
                document.getElementById("broker-count").textContent = (summary.brokers || []).length;
                document.getElementById("topic-count").textContent = summary.topicCount;
                document.getElementById("consumer-group-count").textContent = summary.consumerGroupCount;
                renderBrokers(summary.brokers || []);
            }).catch(function (error) { toast(error.message, true); });
        };
        if (refresh) refresh.addEventListener("click", load);
        load();
    }

    function renderBrokers(brokers) {
        const body = document.getElementById("brokers-body");
        const empty = document.getElementById("brokers-empty");
        const label = document.getElementById("broker-count-label");
        if (label) label.textContent = "共 " + brokers.length + " 个 Broker";
        if (!body) return;
        if (!brokers.length) {
            if (empty) empty.hidden = false;
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = brokers.map(function (b) {
            const role = b.brokerId === 0 ? "Master" : "Slave(" + b.brokerId + ")";
            return "<tr>" +
                "<td><span class='job-name mono'>" + escapeHtml(b.address) + "</span></td>" +
                "<td>" + escapeHtml(b.clusterName) + "</td>" +
                "<td>" + escapeHtml(b.brokerName) + "</td>" +
                "<td>" + escapeHtml(role) + "</td>" +
                "<td>" + escapeHtml(b.version || "-") + "</td>" +
                "</tr>";
        }).join("");
    }

    /* ---------------- Topic 管理 ---------------- */

    function initTopics() {
        const tbody = document.getElementById("topics-body");
        if (!tbody) return;
        const search = document.getElementById("topic-search");
        let all = [];

        const load = function () {
            api("/topics").then(function (topics) {
                all = topics || [];
                render();
            }).catch(function (error) { toast(error.message, true); });
        };
        const render = function () {
            const keyword = (search ? search.value : "").trim().toLowerCase();
            const filtered = keyword ? all.filter(function (t) { return t.topic.toLowerCase().indexOf(keyword) >= 0; }) : all;
            const empty = document.getElementById("topics-empty");
            const label = document.getElementById("topic-count-label");
            if (label) label.textContent = "共 " + filtered.length + " 个业务 Topic";
            if (!filtered.length) {
                if (empty) empty.hidden = false;
                tbody.innerHTML = "";
                return;
            }
            if (empty) empty.hidden = true;
            tbody.innerHTML = filtered.map(function (t) {
                return "<tr>" +
                    "<td><a class='job-name' href='/topics/detail?name=" + encodeURIComponent(t.topic) + "'>" + escapeHtml(t.topic) + "</a></td>" +
                    "<td>" + t.readQueueNums + "</td>" +
                    "<td>" + t.writeQueueNums + "</td>" +
                    "<td>" + escapeHtml(permText(t.perm)) + "</td>" +
                    "<td>" + (t.messageCount === undefined ? "-" : t.messageCount) + "</td>" +
                    "<td><div class='row-actions'>" +
                    "<a class='action-btn action-logs' href='/messages?topic=" + encodeURIComponent(t.topic) + "'>查消息</a>" +
                    "<button class='action-btn action-delete' data-delete-topic='" + escapeHtml(t.topic.replace(/'/g, "\\'")) + "'>删除</button>" +
                    "</div></td></tr>";
            }).join("");
        };
        if (search) search.addEventListener("input", render);
        const refresh = document.getElementById("refresh-button");
        if (refresh) refresh.addEventListener("click", load);

        tbody.addEventListener("click", function (event) {
            const button = event.target.closest("[data-delete-topic]");
            if (!button) return;
            const topic = button.getAttribute("data-delete-topic");
            confirmDialog("确定删除 Topic【" + topic + "】？该操作会删除主题路由信息，生产环境请谨慎。", function () {
                api("/topics/" + encodeURIComponent(topic), { method: "DELETE" }).then(function () {
                    toast("Topic【" + topic + "】已删除");
                    load();
                }).catch(function (error) { toast(error.message, true); });
            }, "确认删除");
        });

        const createButton = document.getElementById("create-topic-button");
        const dialog = document.getElementById("topic-dialog");
        if (createButton) createButton.addEventListener("click", function () { openDialog("topic-dialog"); });
        const form = document.getElementById("topic-form");
        if (form) form.addEventListener("submit", function (event) {
            event.preventDefault();
            const errorEl = document.getElementById("form-error");
            if (errorEl) errorEl.textContent = "";
            const readQueueNums = Number(document.getElementById("read-queue-nums").value);
            const writeQueueNums = Number(document.getElementById("write-queue-nums").value);
            if (!Number.isInteger(readQueueNums) || readQueueNums < 1 || readQueueNums > 1024 ||
                !Number.isInteger(writeQueueNums) || writeQueueNums < 1 || writeQueueNums > 1024) {
                if (errorEl) errorEl.textContent = "读队列数和写队列数必须是 1 到 1024 的整数";
                return;
            }
            const payload = {
                name: document.getElementById("topic-name").value.trim(),
                read_queue_nums: readQueueNums,
                write_queue_nums: writeQueueNums
            };
            api("/topics", { method: "POST", body: payload }).then(function () {
                closeDialog("topic-dialog");
                toast("Topic【" + payload.name + "】创建成功");
                if (form) form.reset();
                load();
            }).catch(function (error) {
                if (errorEl) errorEl.textContent = error.message;
            });
        });

        load();
    }

    /* ---------------- Topic 详情 ---------------- */

    function initTopicDetail() {
        const name = document.getElementById("topic-detail-subtitle");
        if (!name) return;
        const topic = document.querySelector("h1") ? document.querySelector("h1").textContent.trim() : "";
        const load = function () {
            api("/topics/" + encodeURIComponent(topic)).then(function (detail) {
                const readEl = document.getElementById("read-queue-count");
                const writeEl = document.getElementById("write-queue-count");
                const permEl = document.getElementById("topic-perm");
                const consumerEl = document.getElementById("consumer-count");
                if (readEl) readEl.textContent = detail.readQueueNums;
                if (writeEl) writeEl.textContent = detail.writeQueueNums;
                if (permEl) permEl.textContent = permText(detail.perm);
                if (consumerEl) consumerEl.textContent = (detail.consumerGroups || []).length;
                name.textContent = "队列位点与订阅消费组";
                renderQueues(detail.queueOffsets || []);
                renderTopicConsumers(detail.consumerGroups || []);
            }).catch(function (error) {
                name.textContent = error.message;
                toast(error.message, true);
            });
        };
        const refresh = document.getElementById("refresh-button");
        if (refresh) refresh.addEventListener("click", load);
        load();
    }

    function renderQueues(queues) {
        const body = document.getElementById("queues-body");
        const empty = document.getElementById("queues-empty");
        const label = document.getElementById("queues-count-label");
        if (label) label.textContent = "共 " + queues.length + " 个队列分片";
        if (!body) return;
        if (!queues.length) {
            if (empty) empty.hidden = false;
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = queues.map(function (q) {
            const diff = q.maxOffset - q.minOffset;
            return "<tr>" +
                "<td class='mono'>" + escapeHtml(q.brokerName) + "</td>" +
                "<td>" + q.queueId + "</td>" +
                "<td>" + q.minOffset + "</td>" +
                "<td>" + q.maxOffset + "</td>" +
                "<td>" + (diff >= 0 ? diff : 0) + "</td>" +
                "<td>" + fmtTime(q.lastUpdateTimestamp) + "</td>" +
                "</tr>";
        }).join("");
    }

    function renderTopicConsumers(groups) {
        const body = document.getElementById("consumers-body");
        const empty = document.getElementById("consumers-empty");
        const label = document.getElementById("consumers-count-label");
        if (label) label.textContent = "共 " + groups.length + " 个订阅消费组";
        if (!body) return;
        if (!groups.length) {
            if (empty) empty.hidden = false;
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = groups.map(function (group) {
            return "<tr>" +
                "<td><a class='job-name topic-consumer-name' href='/consumer-groups/detail?name=" + encodeURIComponent(group) + "'>" + escapeHtml(group) + "</a></td>" +
                "</tr>";
        }).join("");
    }

    /* ---------------- 消费组 ---------------- */

    function initConsumerGroups() {
        const tbody = document.getElementById("groups-body");
        if (!tbody) return;
        const search = document.getElementById("group-search");
        let all = [];
        const load = function () {
            api("/consumer-groups").then(function (groups) {
                all = groups || [];
                render();
            }).catch(function (error) { toast(error.message, true); });
        };
        const render = function () {
            const keyword = (search ? search.value : "").trim().toLowerCase();
            const filtered = keyword ? all.filter(function (g) { return g.group.toLowerCase().indexOf(keyword) >= 0; }) : all;
            const empty = document.getElementById("groups-empty");
            const label = document.getElementById("group-count-label");
            if (label) label.textContent = "共 " + filtered.length + " 个消费组";
            if (!filtered.length) {
                if (empty) empty.hidden = false;
                tbody.innerHTML = "";
                return;
            }
            if (empty) empty.hidden = true;
            tbody.innerHTML = filtered.map(function (g) {
                const topics = g.topics || [];
                const online = !!g.online;
                return "<tr>" +
                    "<td><a class='job-name' href='/consumer-groups/detail?name=" + encodeURIComponent(g.group) + "'>" + escapeHtml(g.group) + "</a></td>" +
                    "<td>" + escapeHtml(topics.join(", ")) + "</td>" +
                    "<td>" + (typeof g.consumeTps === "number" ? g.consumeTps.toFixed(2) : "-") + "</td>" +
                    "<td>" + (g.diffTotal === undefined ? "-" : g.diffTotal) + "</td>" +
                    "<td>" + statusPill(online ? "在线" : "离线", online) + "</td>" +
                    "</tr>";
            }).join("");
        };
        if (search) search.addEventListener("input", render);
        const refresh = document.getElementById("refresh-button");
        if (refresh) refresh.addEventListener("click", load);
        load();
    }

    /* ---------------- 消费组详情 ---------------- */

    function initConsumerGroupDetail() {
        const subtitle = document.getElementById("group-detail-subtitle");
        if (!subtitle) return;
        const group = document.querySelector("h1").textContent.trim();

        const load = function () {
            api("/consumer-groups/" + encodeURIComponent(group)).then(function (detail) {
                subtitle.textContent = "共 " + (detail.progress || []).length + " 个消费队列，在线客户端 " + (detail.connections || []).length + " 条";
                renderProgress(detail.progress || []);
                renderConnections(detail.connections || []);
            }).catch(function (error) {
                subtitle.textContent = error.message;
                toast(error.message, true);
            });
        };
        const refresh = document.getElementById("refresh-button");
        if (refresh) refresh.addEventListener("click", load);

        const resetButton = document.getElementById("reset-offset-button");
        if (resetButton) resetButton.addEventListener("click", function () { openDialog("reset-dialog"); });
        const form = document.getElementById("reset-form");
        if (form) form.addEventListener("submit", function (event) {
            event.preventDefault();
            const errorEl = document.getElementById("form-error");
            if (errorEl) errorEl.textContent = "";
            const tsInput = document.getElementById("reset-timestamp");
            const ts = tsInput.value ? new Date(tsInput.value).getTime() : null;
            if (!ts) {
                if (errorEl) errorEl.textContent = "请选择重置时间";
                return;
            }
            const payload = {
                topic: document.getElementById("reset-topic").value.trim() || null,
                timestamp: ts,
                force: document.getElementById("reset-force").checked
            };
            api("/consumer-groups/" + encodeURIComponent(group) + "/reset", { method: "POST", body: payload }).then(function () {
                closeDialog("reset-dialog");
                toast("消费组【" + group + "】位点重置已提交");
                load();
            }).catch(function (error) {
                if (errorEl) errorEl.textContent = error.message;
            });
        });

        load();
    }

    function renderProgress(progress) {
        const body = document.getElementById("progress-body");
        const empty = document.getElementById("progress-empty");
        const label = document.getElementById("progress-count-label");
        if (label) label.textContent = "共 " + progress.length + " 个消费队列";
        if (!body) return;
        if (!progress.length) {
            if (empty) empty.hidden = false;
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = progress.map(function (p) {
            const consumerOffset = p.consumerOffset ?? 0;
            const lag = p.diff ?? Math.max(0, (p.brokerOffset || 0) - consumerOffset);
            return "<tr>" +
                "<td>" + escapeHtml(p.topic) + "</td>" +
                "<td class='mono'>" + escapeHtml(p.brokerName) + "</td>" +
                "<td>" + p.queueId + "</td>" +
                "<td>" + consumerOffset + "</td>" +
                "<td>" + p.brokerOffset + "</td>" +
                "<td>" + lag + "</td>" +
                "<td>" + fmtTime(p.lastTimestamp) + "</td>" +
                "</tr>";
        }).join("");
    }

    function renderConnections(connections) {
        const body = document.getElementById("connections-body");
        const empty = document.getElementById("connections-empty");
        const label = document.getElementById("connections-count-label");
        if (label) label.textContent = "共 " + connections.length + " 条客户端连接";
        if (!body) return;
        if (!connections.length) {
            if (empty) empty.hidden = false;
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = connections.map(function (c) {
            return "<tr>" +
                "<td>" + escapeHtml(c.clientId) + "</td>" +
                "<td class='mono'>" + escapeHtml(c.clientAddr) + "</td>" +
                "<td>" + escapeHtml(c.language || "-") + "</td>" +
                "<td>" + escapeHtml(c.version || "-") + "</td>" +
                "</tr>";
        }).join("");
    }

    /* ---------------- 消息查询 ---------------- */

    function initMessageQuery() {
        const topicSelect = document.getElementById("query-topic");
        if (!topicSelect) return;
        const sendTopicSelect = document.getElementById("send-topic");
        let topicMap = {};

        api("/topics").then(function (topics) {
            topicMap = {};
            topics.forEach(function (t) { topicMap[t.topic] = t; });
            topicSelect.innerHTML = '<option value="">请选择 Topic</option>' + topics.map(function (t) {
                return "<option value='" + escapeHtml(t.topic) + "'>" + escapeHtml(t.topic) + "</option>";
            }).join("");
            populateSelect("send-topic", topics);

            const preset = new URLSearchParams(window.location.search).get("topic");
            if (preset) topicSelect.value = preset;
            if (sendTopicSelect) sendTopicSelect.value = topicSelect.value;
        }).catch(function (error) { toast(error.message, true); });

        topicSelect.addEventListener("change", function () {
            if (sendTopicSelect) sendTopicSelect.value = topicSelect.value;
        });

        const end = new Date();
        const begin = new Date(end.getTime() - 3 * 24 * 60 * 60 * 1000);
        document.getElementById("query-begin").value = fmtDateTimeLocal(begin.getTime());
        document.getElementById("query-end").value = fmtDateTimeLocal(end.getTime());

        const form = document.getElementById("query-form");
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            const errorEl = document.getElementById("query-error");
            if (errorEl) errorEl.textContent = "";
            const topic = topicSelect.value;
            if (!topic) {
                if (errorEl) errorEl.textContent = "请选择 Topic";
                return;
            }
            const beginMs = document.getElementById("query-begin").value ? new Date(document.getElementById("query-begin").value).getTime() : null;
            const endMs = document.getElementById("query-end").value ? new Date(document.getElementById("query-end").value).getTime() : null;
            const key = document.getElementById("query-key").value.trim() || null;
            const maxNum = parseInt(document.getElementById("query-max-num").value, 10) || 100;
            const params = new URLSearchParams({ topic: topic, maxNum: String(maxNum) });
            if (key) params.set("key", key);
            if (beginMs) params.set("begin", String(beginMs));
            if (endMs) params.set("end", String(endMs));
            setQueryLoading(true);
            api("/messages/query?" + params.toString()).then(function (list) {
                setQueryLoading(false);
                renderResults(list || []);
            }).catch(function (error) {
                setQueryLoading(false);
                if (errorEl) errorEl.textContent = error.message;
                toast(error.message, true);
            });
        });
        const sendButton = document.getElementById("send-test-button");
        if (sendButton) sendButton.addEventListener("click", function () { openDialog("send-dialog"); });
        const sendForm = document.getElementById("send-form");
        if (sendForm) sendForm.addEventListener("submit", function (event) {
            event.preventDefault();
            const errorEl = document.getElementById("send-form-error");
            if (errorEl) errorEl.textContent = "";
            const payload = {
                topic: document.getElementById("send-topic").value,
                body: document.getElementById("send-body").value,
                tags: document.getElementById("send-tags").value.trim() || null,
                keys: document.getElementById("send-keys").value.trim() || null,
                delayLevel: parseInt(document.getElementById("send-delay-level").value, 10) || 0
            };
            if (!payload.topic || !payload.body) {
                if (errorEl) errorEl.textContent = "Topic 与消息内容必填";
                return;
            }
            api("/messages/send", { method: "POST", body: payload }).then(function (result) {
                closeDialog("send-dialog");
                toast("消息已发送：" + result.msgId);
                sendForm.reset();
                sendForm.dispatchEvent(new Event("reset"));
            }).catch(function (error) {
                if (errorEl) errorEl.textContent = error.message;
            });
        });
    }

    function populateSelect(id, topics) {
        const el = document.getElementById(id);
        if (!el) return;
        el.innerHTML = topics.map(function (t) {
            return "<option value='" + escapeHtml(t.topic) + "'>" + escapeHtml(t.topic) + "</option>";
        }).join("");
    }

    function setQueryLoading(loading) {
        const loadingEl = document.getElementById("results-loading");
        const emptyEl = document.getElementById("results-empty");
        const bodyEl = document.getElementById("results-body");
        if (loadingEl) loadingEl.hidden = !loading;
        if (emptyEl && loading) emptyEl.hidden = true;
        if (bodyEl && loading) bodyEl.innerHTML = "";
    }

    function renderResults(list) {
        const body = document.getElementById("results-body");
        const empty = document.getElementById("results-empty");
        const label = document.getElementById("result-count-label");
        if (label) label.textContent = "共查询到 " + list.length + " 条消息";
        if (!body) return;
        if (!list.length) {
            if (empty) {
                empty.textContent = "未查询到消息";
                empty.hidden = false;
            }
            body.innerHTML = "";
            return;
        }
        if (empty) empty.hidden = true;
        body.innerHTML = list.map(function (m) {
            const tagKey = [m.tags, m.keys].filter(Boolean).join(" / ");
            const isDlq = !!m.deadLetter;
            return "<tr>" +
                "<td><a class='job-name mono' href='/messages/detail?id=" + encodeURIComponent(m.msgId) + "&topic=" + encodeURIComponent(m.topic) + "'>" + escapeHtml(m.msgId) + "</a></td>" +
                "<td>" + escapeHtml(m.topic) + (isDlq ? statusPill("死信", false) : "") + "</td>" +
                "<td>" + escapeHtml(tagKey || "-") + "</td>" +
                "<td>" + fmtTime(m.storeTimestamp) + "</td>" +
                "<td>" + m.reconsumeTimes + "</td>" +
                "<td><div class='row-actions'>" +
                "<a class='action-btn action-logs' href='/messages/detail?id=" + encodeURIComponent(m.msgId) + "&topic=" + encodeURIComponent(m.topic) + "'>详情</a>" +
                "</div></td></tr>";
        }).join("");
    }

    /* ---------------- 消息详情 ---------------- */

    function initMessageDetail() {
        const subtitle = document.getElementById("message-subtitle");
        if (!subtitle) return;
        const msgId = document.getElementById("msg-id") ? document.getElementById("msg-id").value : "";
        const topic = document.getElementById("msg-topic") ? document.getElementById("msg-topic").value : "";
        let current = null;
        let showHex = false;

        const load = function () {
            api("/messages/" + encodeURIComponent(msgId) + "?topic=" + encodeURIComponent(topic)).then(function (message) {
                current = message;
                renderMeta(message);
                renderBody(message);
                renderProps(message.properties || {});
                subtitle.textContent = message.topic + (message.deadLetter ? " · 死信" : "") + " · " + message.bodySize + " 字节";
            }).catch(function (error) {
                subtitle.textContent = error.message;
                toast(error.message, true);
            });
        };

        function renderMeta(message) {
            const list = document.getElementById("meta-list");
            if (!list) return;
            const rows = [
                ["消息 ID", message.msgId],
                ["Topic", message.topic],
                ["Tag", message.tags || "-"],
                ["Key", message.keys || "-"],
                ["生产端", message.bornHost],
                ["存储端", message.storeHost],
                ["队列", message.queueId + " / " + message.queueOffset],
                ["生产时间", fmtTime(message.bornTimestamp)],
                ["存储时间", fmtTime(message.storeTimestamp)],
                ["重试次数", String(message.reconsumeTimes)],
                ["延迟等级", String(message.delayLevel)]
            ];
            list.innerHTML = rows.map(function (pair) {
                return "<div class='detail-row'><dt>" + escapeHtml(pair[0]) + "</dt><dd class='mono'>" + escapeHtml(pair[1]) + "</dd></div>";
            }).join("");
        }

        function renderBody(message) {
            const el = document.getElementById("message-body");
            const label = document.getElementById("body-size-label");
            const toggle = document.getElementById("body-toggle");
            if (!el) return;
            if (label) label.textContent = "大小 " + message.bodySize + " 字节";
            if (showHex) {
                el.textContent = message.bodyHex || "(空)";
                if (toggle) toggle.textContent = "显示文本";
            } else {
                el.textContent = message.bodyText || message.bodyHex || "(空)";
                if (toggle) toggle.textContent = message.bodyHex ? "显示十六进制" : "十六进制不可用";
                if (toggle) toggle.disabled = !message.bodyHex;
            }
        }

        function renderProps(props) {
            const body = document.getElementById("props-body");
            const empty = document.getElementById("props-empty");
            if (!body) return;
            const entries = Object.keys(props).sort();
            if (!entries.length) {
                if (empty) empty.hidden = false;
                body.innerHTML = "";
                return;
            }
            if (empty) empty.hidden = true;
            body.innerHTML = entries.map(function (key) {
                return "<tr><td>" + escapeHtml(key) + "</td><td class='mono'>" + escapeHtml(props[key]) + "</td></tr>";
            }).join("");
        }

        const refresh = document.getElementById("refresh-button");
        if (refresh) refresh.addEventListener("click", load);
        const toggle = document.getElementById("body-toggle");
        if (toggle) toggle.addEventListener("click", function () { showHex = !showHex; if (current) renderBody(current); });

        const resendButton = document.getElementById("resend-button");
        if (resendButton) resendButton.addEventListener("click", function () { openDialog("resend-dialog"); });
        const resendForm = document.getElementById("resend-form");
        if (resendForm) resendForm.addEventListener("submit", function (event) {
            event.preventDefault();
            const errorEl = document.getElementById("resend-form-error");
            if (errorEl) errorEl.textContent = "";
            const payload = {
                topic: topic,
                targetTopic: document.getElementById("resend-topic").value.trim() || null,
                delayLevel: parseInt(document.getElementById("resend-delay-level").value, 10) || 0
            };
            api("/messages/" + encodeURIComponent(msgId) + "/resend", { method: "POST", body: payload }).then(function (result) {
                closeDialog("resend-dialog");
                toast("消息已重发：" + result.targetTopic);
            }).catch(function (error) {
                if (errorEl) errorEl.textContent = error.message;
            });
        });

        load();
    }

    /* ---------------- 入口 ---------------- */

    document.addEventListener("DOMContentLoaded", function () {
        bindCloseButtons();
        const page = currentPage();
        if (page === "dashboard") initDashboard();
        else if (page === "topics") {
            if (document.getElementById("topics-body")) initTopics();
            else if (document.getElementById("queues-body")) initTopicDetail();
        }
        else if (page === "consumer-groups") {
            if (document.getElementById("groups-body")) initConsumerGroups();
            else if (document.getElementById("progress-body")) initConsumerGroupDetail();
        }
        else if (page === "messages") {
            if (document.getElementById("query-topic")) initMessageQuery();
            else if (document.getElementById("message-body")) initMessageDetail();
        }
    });
})();
