package com.griefprevention.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClaimToolHandlerRegistry
{
    private final CopyOnWriteArrayList<ClaimToolHandler> handlers = new CopyOnWriteArrayList<>();

    public void register(ClaimToolHandler handler)
    {
        if (handler == null || handlers.contains(handler))
        {
            return;
        }

        handlers.add(handler);
        handlers.sort(Comparator.comparingInt((ClaimToolHandler h) -> h.getPriority()).reversed());
    }

    public void unregister(ClaimToolHandler handler)
    {
        handlers.remove(handler);
    }

    public List<ClaimToolHandler> getHandlers()
    {
        return new ArrayList<>(handlers);
    }

    public boolean dispatch(ClaimToolContext context)
    {
        for (ClaimToolHandler handler : handlers)
        {
            if (handler.handle(context))
            {
                return true;
            }
        }

        return false;
    }
}
