package io.github.hao1196561270.mifitnessadaway;

import android.app.Application;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块 App：连接 LSPosed 框架服务，供设置页读写 RemotePreferences。
 */
public class MiFitnessApp extends Application implements XposedServiceHelper.OnServiceListener {

    private final Set<XposedServiceHelper.OnServiceListener> listeners =
            new CopyOnWriteArraySet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    public void addServiceStateListener(XposedServiceHelper.OnServiceListener l, boolean notify) {
        listeners.add(l);
        if (notify && mService != null) {
            l.onServiceBind(mService);
        }
    }

    public void removeServiceStateListener(XposedServiceHelper.OnServiceListener l) {
        listeners.remove(l);
    }

    private XposedService mService;

    @Override
    public void onServiceBind(XposedService service) {
        mService = service;
        for (XposedServiceHelper.OnServiceListener l : listeners) {
            l.onServiceBind(service);
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        mService = null;
        for (XposedServiceHelper.OnServiceListener l : listeners) {
            l.onServiceDied(service);
        }
    }
}