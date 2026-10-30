package com.associago.activity;

import com.associago.activity.dto.ActivityParticipantDTO;
import org.springframework.beans.BeanUtils;

/** Copies only: response redaction must never change attached persistent entities. */
public final class ActivityPrivacy {
    private ActivityPrivacy() {}
    public static Activity basic(Activity source) {
        var copy = new Activity(); BeanUtils.copyProperties(source, copy); copy.setCost(null); return copy;
    }
    public static ActivityInstructor instructor(ActivityInstructor source) {
        var copy = new ActivityInstructor(); BeanUtils.copyProperties(source, copy);
        copy.setCompensation(null); copy.setCompensationType(null); return copy;
    }
    public static ActivityParticipantDTO participant(ActivityParticipant source, boolean financial) {
        var user = source.getUser();
        return new ActivityParticipantDTO(source.getId(), source.getActivityId(), user == null ? null :
                new ActivityParticipantDTO.UserName(user.getId(), user.getFirstName(), user.getLastName()),
                source.getRegistrationDate(), source.getExpirationDate(), financial ? source.isPaid() : null,
                financial ? source.getAmountPaid() : null, financial ? source.getInvoiceId() : null,
                financial ? source.getPaymentMethod() : null, financial ? source.getPaymentDate() : null,
                source.getNotes(), source.isActive(), source.getCancellationDate(), source.getCancellationReason(),
                source.getCreatedAt(), source.getUpdatedAt());
    }
}
