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

    function fetchJson(url) {
        return fetch(url, { headers: { "Accept": "application/json" } }).then(function (r) {
            if (!r.ok) throw new Error("HTTP " + r.status);
            return r.json();
        });
    }

    if (PAGE !== "topology") return;

    var canvas = document.getElementById("topo-canvas");
    var topoBody = document.getElementById("topo-body");
    var topoLabel = document.getElementById("topo-label");
    var emptyState = document.getElementById("empty");
    var filterInput = document.getElementById("filter-service");
    var statServices = document.getElementById("stat-services");
    var statLinks = document.getElementById("stat-links");
    var statCalls = document.getElementById("stat-calls");
    var statErrRate = document.getElementById("stat-err-rate");
    var allLinks = [];

    var NS = "http://www.w3.org/2000/svg";

    var ROLE_COLORS = {
        in: "#2563eb",
        mid: "#ea8c2d",
        out: "#16a34a"
    };
    var ROLE_NAMES = { in: "入口", mid: "中间", out: "终端" };

    // ── 布局参数 ─────────────────────────────────────────────
    var NODE_W = 210;
    var NODE_H = 84;
    var LAYER_GAP = 360;
    var NODE_GAP = 110;
    var PAD_X = 90;
    var PAD_Y = 70;

    function el(tag, attrs) {
        var e = document.createElementNS(NS, tag);
        if (attrs) Object.keys(attrs).forEach(function (k) { e.setAttribute(k, attrs[k]); });
        return e;
    }

    function svgText(content, attrs) {
        var t = el("text", attrs);
        t.textContent = content;
        return t;
    }

    function shortName(name) {
        return name.replace(/^infra-trace-/, "").replace(/^infra-/, "");
    }

    function computeLayout(links) {
        var serviceSet = {};
        links.forEach(function (l) {
            serviceSet[l.source] = true;
            serviceSet[l.target] = true;
        });
        var services = Object.keys(serviceSet);

        var incoming = {}, outgoing = {};
        services.forEach(function (s) { incoming[s] = []; outgoing[s] = []; });
        links.forEach(function (l) {
            outgoing[l.source].push(l.target);
            incoming[l.target].push(l.source);
        });

        var layerOf = {};
        function assignLayer(svc, layer) {
            if (layerOf[svc] !== undefined && layerOf[svc] >= layer) return;
            layerOf[svc] = layer;
            outgoing[svc].forEach(function (next) { assignLayer(next, layer + 1); });
        }
        services.forEach(function (s) { if (incoming[s].length === 0) assignLayer(s, 0); });
        services.forEach(function (s) { if (layerOf[s] === undefined) assignLayer(s, 0); });

        var layers = {};
        services.forEach(function (s) {
            var L = layerOf[s] || 0;
            if (!layers[L]) layers[L] = [];
            layers[L].push(s);
        });
        var maxLayer = Math.max.apply(null, Object.keys(layers).map(Number));

        var maxNodesInLayer = 1;
        for (var L = 0; L <= maxLayer; L++) {
            if (layers[L] && layers[L].length > maxNodesInLayer) maxNodesInLayer = layers[L].length;
        }

        var W = PAD_X * 2 + (maxLayer + 1) * NODE_W + maxLayer * (LAYER_GAP - NODE_W);
        var H = PAD_Y * 2 + maxNodesInLayer * NODE_H + (maxNodesInLayer - 1) * NODE_GAP;
        W = Math.max(W, 560);
        H = Math.max(H, 240);

        var positions = {};
        for (var L = 0; L <= maxLayer; L++) {
            var nodesInLayer = layers[L] || [];
            var x = PAD_X + L * LAYER_GAP + NODE_W / 2;
            var totalH = nodesInLayer.length * NODE_H + (nodesInLayer.length - 1) * NODE_GAP;
            var startY = (H - totalH) / 2;
            nodesInLayer.forEach(function (svc, i) {
                positions[svc] = { x: x, y: startY + i * (NODE_H + NODE_GAP) + NODE_H / 2 };
            });
        }

        return { services: services, incoming: incoming, outgoing: outgoing, positions: positions, W: W, H: H };
    }

    function buildTopology(links) {
        canvas.innerHTML = "";
        topoBody.innerHTML = "";
        statServices.textContent = "-";
        statLinks.textContent = "-";
        statCalls.textContent = "-";
        statErrRate.textContent = "-";

        if (!links || !links.length) {
            topoLabel.textContent = "暂无跨服务调用数据";
            emptyState.hidden = false;
            return;
        }
        emptyState.hidden = true;
        topoLabel.textContent = "共 " + links.length + " 条调用关系";

        var totalCalls = 0, totalErrs = 0;
        links.forEach(function (l) {
            totalCalls += l.callCount;
            totalErrs += l.errorCount;
        });

        var layout = computeLayout(links);
        statServices.textContent = layout.services.length;
        statLinks.textContent = links.length;
        statCalls.textContent = totalCalls;
        statErrRate.textContent = (totalCalls ? (totalErrs / totalCalls * 100).toFixed(1) : "0.0") + "%";

        var svg = el("svg", {
            width: layout.W, height: layout.H,
            viewBox: "0 0 " + layout.W + " " + layout.H,
            class: "topo-svg"
        });

        var defs = el("defs");

        // 箭头标记（成功 / 异常）
        ["ok", "err"].forEach(function (type) {
            var marker = el("marker", {
                id: "arr-" + type, viewBox: "0 0 10 10", refX: "8.5", refY: "5",
                markerWidth: "5.5", markerHeight: "5.5", orient: "auto"
            });
            marker.appendChild(el("path", {
                d: "M0,1 L9,5 L0,9 Z",
                fill: type === "err" ? "#e0492f" : "#3b82f6"
            }));
            defs.appendChild(marker);
        });

        // 节点投影
        var filter = el("filter", { id: "nshadow", x: "-12%", y: "-12%", width: "124%", height: "140%" });
        filter.appendChild(el("feDropShadow", { dx: "0", dy: "3", stdDeviation: "6", "flood-color": "rgba(0,0,0,0.55)" }));
        defs.appendChild(filter);
        svg.appendChild(defs);

        // ── 先画链路（在节点下层） ─────────────────────────────
        var maxCalls = Math.max.apply(null, links.map(function (l) { return l.callCount; }));
        var linkGroups = {};

        links.forEach(function (l) {
            var p1 = layout.positions[l.source];
            var p2 = layout.positions[l.target];
            if (!p1 || !p2) return;

            var hasError = l.errorCount > 0;
            var ratio = maxCalls ? l.callCount / maxCalls : 1;
            var strokeW = 1.8 + ratio * 2.6;

            var x1 = p1.x + NODE_W / 2 + 3;
            var y1 = p1.y;
            var x2 = p2.x - NODE_W / 2 - 3;
            var y2 = p2.y;

            var dx = x2 - x1;
            var cp1x = x1 + dx * 0.45;
            var cp2x = x2 - dx * 0.45;
            var d = "M" + x1 + "," + y1 + " C" + cp1x + "," + y1 + " " + cp2x + "," + y2 + " " + x2 + "," + y2;

            var color = hasError ? "#e0492f" : "#3b82f6";

            var g = el("g", { class: "topo-link-g" });

            // 阴影
            g.appendChild(el("path", {
                d: d, stroke: "rgba(0,0,0,0.45)", "stroke-width": strokeW + 3,
                fill: "none", "stroke-linecap": "round"
            }));

            // 主链路 + 流动虚线
            var main = el("path", {
                d: d, stroke: color, "stroke-width": strokeW,
                fill: "none", "stroke-linecap": "round",
                "marker-end": "url(#arr-" + (hasError ? "err" : "ok") + ")"
            });
            var flow = el("path", {
                d: d, stroke: hasError ? "#f0a08c" : "#93c5fd",
                "stroke-width": strokeW * 0.45,
                fill: "none", "stroke-linecap": "round",
                "stroke-dasharray": "6,12", class: "topo-flow"
            });
            g.appendChild(main);
            g.appendChild(flow);

            // 调用量标签（曲线中点上方，底色白底圆角）
            var midX = (x1 + x2) / 2;
            var midY = (y1 + y2) / 2;
            var pillH = 22;
            var pillW = Math.max(34, String(l.callCount).length * 9 + 16);
            var pill = el("g", { class: "topo-pill", transform: "translate(" + (midX - pillW / 2) + "," + (midY - pillH / 2 - 14) + ")" });
            pill.appendChild(el("rect", {
                width: pillW, height: pillH, rx: pillH / 2,
                fill: "#ffffff",
                stroke: hasError ? "#efb3ad" : "#c9def4",
                "stroke-width": 1.2
            }));
            var countTxt = svgText(l.callCount, {
                x: pillW / 2, y: pillH / 2 + 4, "text-anchor": "middle",
                fill: hasError ? "#c0352b" : "#2f6fb0",
                class: "topo-pill-count"
            });
            pill.appendChild(countTxt);
            g.appendChild(pill);

            svg.appendChild(g);
            linkGroups[l.source + ">" + l.target] = g;

            // 明细表
            var tr = document.createElement("tr");
            tr.innerHTML =
                "<td><span class=\"svc-pill\" data-role=\"" + roleOf(l.source, layout) + "\"></span>" + escapeHtml(shortName(l.source)) + "</td>" +
                "<td><span class=\"svc-pill\" data-role=\"" + roleOf(l.target, layout) + "\"></span>" + escapeHtml(shortName(l.target)) + "</td>" +
                "<td>" + l.callCount + "</td>" +
                '<td class="' + (l.errorCount > 0 ? "err-count" : "") + '">' + l.errorCount + "</td>" +
                "<td>" + l.avgDurationMillis.toFixed(1) + " ms</td>";
            topoBody.appendChild(tr);
        });

        // ── 画服务节点（在最上层） ─────────────────────────────
        var nodeGroups = {};
        layout.services.forEach(function (svc) {
            var pos = layout.positions[svc];
            if (!pos) return;

            var role = roleOf(svc, layout);
            var roleColor = ROLE_COLORS[role];
            var cx = pos.x;
            var cy = pos.y;

            var g = el("g", { class: "topo-node", transform: "translate(" + (cx - NODE_W / 2) + "," + (cy - NODE_H / 2) + ")" });
            var nodeKey = svc;
            nodeGroups[nodeKey] = g;

            // 卡片背景
            g.appendChild(el("rect", {
                width: NODE_W, height: NODE_H, rx: 14,
                class: "topo-node-bg", filter: "url(#nshadow)"
            }));

            // 顶部角色色条
            g.appendChild(el("rect", {
                x: 1, y: 1, width: NODE_W - 2, height: 6, rx: 3,
                fill: roleColor, opacity: "0.9", class: "topo-node-topbar"
            }));

            // 角色图标（圆角方块 + 内部图形）
            var iconSize = 34;
            var iconX = 16, iconY = 20;
            var icon = el("rect", {
                x: iconX, y: iconY, width: iconSize, height: iconSize, rx: 10,
                fill: roleColor, class: "topo-node-icon"
            });
            var iconText = role === "in" ? "→" : (role === "mid" ? "⇄" : "↘");
            var it = svgText(iconText, {
                x: iconX + iconSize / 2, y: iconY + iconSize / 2 + 5,
                "text-anchor": "middle", fill: "#fff", class: "topo-node-glyph"
            });
            g.appendChild(icon);
            g.appendChild(it);

            // 服务名（清除前缀）
            var nameTxt = svgText(shortName(svc), {
                x: iconX + iconSize + 12, y: 34, class: "topo-node-name"
            });
            g.appendChild(nameTxt);

            // 角色标签 + 调用统计
            var outCount = layout.outgoing[svc].length;
            var inCount = layout.incoming[svc].length;
            var totalForSvc = countTotalFor(links, svc);
            var subTxt = svgText(
                ROLE_NAMES[role] + " · 调用 " + totalForSvc + " 次",
                { x: iconX + iconSize + 12, y: 56, class: "topo-node-sub" }
            );
            g.appendChild(subTxt);

            // hover：联动高亮
            g.addEventListener("mouseenter", function () {
                highlightRelated(svc, links, layout, linkGroups, nodeGroups);
            });
            g.addEventListener("mouseleave", function () {
                clearHighlight(linkGroups, nodeGroups);
            });

            svg.appendChild(g);
        });

        canvas.appendChild(svg);
    }

    function roleOf(svc, layout) {
        var hasIn = layout.incoming[svc] && layout.incoming[svc].length > 0;
        var hasOut = layout.outgoing[svc] && layout.outgoing[svc].length > 0;
        if (!hasIn) return "in";
        if (!hasOut) return "out";
        return "mid";
    }

    function countTotalFor(links, svc) {
        var total = 0;
        links.forEach(function (l) {
            if (l.source === svc || l.target === svc) total += l.callCount;
        });
        return total;
    }

    function highlightRelated(svc, links, layout, linkGroups, nodeGroups) {
        var relatedServices = {};
        links.forEach(function (l) {
            if (l.source === svc) {
                relatedServices[l.source] = true;
                relatedServices[l.target] = true;
                var g = linkGroups[l.source + ">" + l.target];
                if (g) g.classList.add("hl");
            } else if (l.target === svc) {
                relatedServices[l.source] = true;
                relatedServices[l.target] = true;
                var g2 = linkGroups[l.source + ">" + l.target];
                if (g2) g2.classList.add("hl");
            } else {
                var g3 = linkGroups[l.source + ">" + l.target];
                if (g3) g3.classList.add("dim");
            }
        });
        layout.services.forEach(function (s) {
            var g = nodeGroups[s];
            if (g) {
                if (relatedServices[s]) g.classList.add("hl");
                else g.classList.add("dim");
            }
        });
    }

    function clearHighlight(linkGroups, nodeGroups) {
        Object.keys(linkGroups).forEach(function (k) {
            linkGroups[k].classList.remove("hl", "dim");
        });
        Object.keys(nodeGroups).forEach(function (k) {
            nodeGroups[k].classList.remove("hl", "dim");
        });
    }

    function loadTopology() {
        fetchJson("/api/trace/topology?limit=500")
            .then(function (links) {
                allLinks = links;
                buildTopology(links);
            })
            .catch(function (err) {
                showToast("加载拓扑失败：" + err.message, true);
            });
    }

    filterInput.addEventListener("input", function () {
        var q = filterInput.value.trim().toLowerCase();
        var filtered = q ? allLinks.filter(function (l) {
            return l.source.toLowerCase().indexOf(q) !== -1 || l.target.toLowerCase().indexOf(q) !== -1;
        }) : allLinks;
        buildTopology(filtered);
    });

    document.getElementById("refresh").addEventListener("click", loadTopology);
    loadTopology();
})();
