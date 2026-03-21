package com.griefprevention.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for addon claim tool handlers.
 */
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
        handlers.sort(Comparator.comparingInt(ClaimToolHandler::getPriority).reversed());
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
