package village.dungeonguard.client;

import village.dungeonguard.AreaPresentation;
import village.dungeonguard.AreaState;

public final class AreaBannerState {
    private AreaState area = AreaState.EMPTY;
    private int remainingTicks;
    private float opacity;

    public void accept(AreaState incoming) {
        if (incoming == null || incoming.isEmpty()) {
            this.clear();
            return;
        }
        if (!AreaPresentation.name(incoming).equals(AreaPresentation.name(this.area))) {
            this.opacity = 0.0f;
        }
        this.area = incoming;
        this.remainingTicks = 200;
    }

    public void tick() {
        if (this.remainingTicks <= 0 || --this.remainingTicks == 0) {
            this.clear();
            return;
        }
        this.opacity = Math.min(1.0f, this.opacity + 0.1f);
    }

    public void clear() {
        this.area = AreaState.EMPTY;
        this.remainingTicks = 0;
        this.opacity = 0.0f;
    }

    public AreaState area() {
        return this.area;
    }

    public float opacity() {
        return this.opacity;
    }
}

