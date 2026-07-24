package com.book.mask.floating;

final class PackageTransitionDecision {
    enum Action {
        PAUSE_AND_RECHECK,
        RESUME_DETECTION
    }

    private PackageTransitionDecision() {
    }

    static Action decide(String targetPackage, String confirmedPackage) {
        return targetPackage.equals(confirmedPackage)
                ? Action.PAUSE_AND_RECHECK
                : Action.RESUME_DETECTION;
    }
}
