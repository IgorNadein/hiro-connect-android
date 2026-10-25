package ru.hiro.manager

internal fun isCorpProvider(name: String?): Boolean =
    name?.trim()?.lowercase() in setOf("corp", "corp - hiro")

internal fun oidcButtonLabel(name: String?): String = when {
    isCorpProvider(name) -> "Войти через CORP - HiRo"
    name.isNullOrBlank() -> "Войти через SSO"
    else -> "Войти через ${name.trim().take(80)}"
}

internal fun serverErrorMessage(status: Int, authenticated: Boolean, path: String, message: String?): String {
    val detail = message?.trim()?.takeIf { it.isNotEmpty() }
    return when (status) {
        401 -> if (authenticated) "Сессия завершена" else "Неверный логин или пароль"
        403 -> "Недостаточно прав для этого действия"
        412 -> "Настройки изменены другим пользователем. Обновите список и повторите изменение"
        429 -> "Слишком много попыток. Попробуйте позже"
        404 -> detail ?: if (path == "/.well-known/hiro/client")
            "По этому адресу сервер HI-RO не найден. Проверьте адрес сервера организации."
        else "Этот раздел недоступен на сервере. Обратитесь к администратору для проверки версии и настроек сервера."
        502, 504 -> detail ?: "Не удалось связаться с сервером организации. Попробуйте позже."
        503 -> detail ?: "Сервис временно недоступен. Попробуйте позже."
        else -> detail ?: "Сервер вернул ошибку $status"
    }
}

internal fun journalProblemText(problem: String): String? = problem.trim().takeIf { it.isNotEmpty() }
internal fun liveUnavailableText(problem: String): String = journalProblemText(problem)
    ?: "Нет актуального состояния АТС. Подключение к разговору недоступно."
