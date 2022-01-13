/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.commons.lang.RandomStringUtils;

/**
 * Abstract base class for {@link ByteBuf} implementations that count references.
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractReferenceCountedByteBuf.class);
    private static final long REFCNT_FIELD_OFFSET =
            ReferenceCountUpdater.getUnsafeOffset(AbstractReferenceCountedByteBuf.class, "refCnt");
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> AIF_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");
    private String name = RandomStringUtils.randomAlphanumeric(16);
    private static final ReferenceCountUpdater<AbstractReferenceCountedByteBuf> updater =
            new ReferenceCountUpdater<AbstractReferenceCountedByteBuf>() {
        @Override
        protected AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> updater() {
            return AIF_UPDATER;
        }
        @Override
        protected long unsafeOffset() {
            return REFCNT_FIELD_OFFSET;
        }
    };
    // Value might not equal "real" reference count, all access should be via the updater
    @SuppressWarnings("unused")
    private volatile int refCnt = updater.initialValue();

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("name : ").append(name).append(", ")
                .append(super.toString());
        return sb.toString();
    }

    @Override
    boolean isAccessible() {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return updater.isLiveNonVolatile(this);
    }

    @Override
    public int refCnt() {
        return updater.refCnt(this);
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int refCnt) {
        updater.setRefCnt(this, refCnt);
    }

    /**
     * An unsafe operation intended for use by a subclass that resets the reference count of the buffer to 1
     */
    protected final void resetRefCnt() {
        updater.resetRefCnt(this);
    }

    @Override
    public ByteBuf retain() {
        if (logger.isDebugEnabled()) {
            logger.debug("ByteBuf : " + this + " retain, before : " + updater.refCnt(this));
        }
        return updater.retain(this);
    }

    @Override
    public ByteBuf retain(int increment) {
        if (logger.isDebugEnabled()) {
            logger.debug("ByteBuf : " + this + " retain with increment" + increment
                    + ", before : " + updater.refCnt(this));
        }
        return updater.retain(this, increment);
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        boolean release = handleRelease(updater.release(this));
        if (logger.isDebugEnabled()) {
            logger.debug("ByteBuf : " + this + " release, after : " + updater.refCnt(this));
            if (release) {
                logger.debug("ByteBuf : " + this + " is going to deallocate ");
            }
        }
        return release;
    }

    @Override
    public boolean release(int decrement) {
        boolean release = handleRelease(updater.release(this));
        if (logger.isDebugEnabled()) {
            logger.debug("ByteBuf : " + this + " release with decrement" + decrement
                    + ", after : " + updater.refCnt(this));
            if (release) {
                logger.debug("ByteBuf is going to deallocate ");
            }
        }
        return release;
    }

    private boolean handleRelease(boolean result) {
        if (result) {
            if (logger.isDebugEnabled()) {
                logger.debug("handleRelease, deallocate ByteBuf : " + this);
            }
            deallocate();
        }
        return result;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
