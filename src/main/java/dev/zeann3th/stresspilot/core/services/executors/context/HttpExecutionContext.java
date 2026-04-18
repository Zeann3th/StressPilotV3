package dev.zeann3th.stresspilot.core.services.executors.context;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpExecutionContext implements CookieJar {
    private final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(@NotNull HttpUrl url, @NotNull List<Cookie> cookies) {
        cookieStore.put(url.host(), new ArrayList<>(cookies));
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl url) {
        List<Cookie> cookies = cookieStore.get(url.host());
        if (cookies == null) {
            return Collections.emptyList();
        }

        List<Cookie> validCookies = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (cookie.expiresAt() > System.currentTimeMillis()) {
                validCookies.add(cookie);
            }
        }
        return validCookies;
    }

    public void clear() {
        cookieStore.clear();
    }
}
