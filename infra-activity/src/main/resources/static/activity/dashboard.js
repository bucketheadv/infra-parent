(() => {
    "use strict";

    const THEME_STORAGE_KEY = "infra_activity_theme";
    const supportedThemes = ["emerald", "green", "orange", "red", "ocean", "violet"];

    // 与活动配置页共用主题存储键，确保在任一页面切换后均可保持一致。
    const applyTheme = (theme) => {
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
            // 本地存储不可用时，仅在当前页面应用主题色。
        }
    };

    document.querySelectorAll(".theme-option").forEach((button) => {
        button.addEventListener("click", () => applyTheme(button.dataset.theme));
    });
    document.getElementById("open-personal-settings").addEventListener("click", (event) => {
        event.currentTarget.closest(".account-menu")?.removeAttribute("open");
        document.getElementById("personal-settings-dialog").showModal();
    });

    document.querySelectorAll("dialog").forEach((dialog) => {
        dialog.addEventListener("click", (event) => {
            if (event.target === dialog) dialog.close("cancel");
        });
    });

    try {
        applyTheme(window.localStorage.getItem(THEME_STORAGE_KEY) || "emerald");
    } catch (_) {
        applyTheme("emerald");
    }
})();
