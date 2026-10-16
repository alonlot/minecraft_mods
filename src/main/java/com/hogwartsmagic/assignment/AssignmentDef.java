package com.hogwartsmagic.assignment;

import java.util.List;

/**
 * The assignment that teaches one spell. {@code teacher} picks the signature and subject lines;
 * {@code dark} marks Dark Orders (black parchment, no Hogwarts teacher).
 */
public record AssignmentDef(String spellId, String teacher, boolean dark, List<ObjectiveDef> objectives) {
    public AssignmentDef {
        objectives = List.copyOf(objectives);
    }

    /** Lang key of objective {@code index}'s text; its single argument is the count. */
    public String objectiveKey(int index) {
        return "assignment.hogwartsmagic." + this.spellId + "." + index;
    }

    public String quoteKey() {
        return "assignment.hogwartsmagic." + this.spellId + ".quote";
    }

    public String teacherKey() {
        return "assignment.hogwartsmagic.teacher." + this.teacher;
    }
}
