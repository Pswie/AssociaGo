package com.associago.attachment;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("desktop")
class DesktopAttachmentBinaryTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("associago.data.path", () -> Path.of(System.getProperty("associago.data.path"), "binary-roundtrip").toString());
    }

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void postgresCompatibleMappingPreservesSqliteBinaryPayload() throws Exception {
        byte[] bytes = new byte[131_072];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i % 251);
        Attachment attachment = new Attachment();
        attachment.setOwnerType(AttachmentOwner.VOLUNTEER_EXPENSE);
        attachment.setOwnerId(999_003L);
        attachment.setKind("sqlite-roundtrip");
        attachment.setFileName("roundtrip.bin");
        attachment.setContent(bytes);
        attachment.setSizeBytes(bytes.length);
        attachment.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        entityManager.persist(attachment);
        entityManager.flush();
        Long id = attachment.getId();
        entityManager.clear();

        assertThat(entityManager.find(Attachment.class, id).getContent()).containsExactly(bytes);
    }
}
