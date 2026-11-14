package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FabricMutationContextTest {
    @Test void nestedOperationsRestoreTheirParentAfterFailure() {
        var outer = new FabricMutationContext.Context(null, null, true, false, false, false);
        var inner = new FabricMutationContext.Context(null, null, false, true, false, false);
        assertNull(FabricMutationContext.current());
        FabricMutationContext.during(outer, () -> {
            assertSame(outer, FabricMutationContext.current());
            assertThrows(IllegalStateException.class, () -> FabricMutationContext.during(inner, () -> {
                assertSame(inner, FabricMutationContext.current());
                throw new IllegalStateException("vanilla operation failed");
            }));
            assertSame(outer, FabricMutationContext.current());
            return null;
        });
        assertNull(FabricMutationContext.current());
    }
    @Test void failedTopLevelOperationLeavesNoProtectionScopeBehind() {
        var context = new FabricMutationContext.Context(null, null, false, false, false, true);
        assertThrows(IllegalArgumentException.class, () -> FabricMutationContext.during(context, () -> {
            throw new IllegalArgumentException();
        }));
        assertNull(FabricMutationContext.current());
    }
}
