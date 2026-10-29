package com.associago.security;

import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Risolve l'anagrafica interna a partire dall'identità Clerk.
 *
 * <p>È il perno dell'area personale: senza una risposta affidabile alla domanda
 * «chi è chi sta chiamando», un socio potrebbe chiedere i dati di un altro
 * passando un id diverso nell'URL.
 *
 * <p>La ricerca procede in due tempi:
 * <ol>
 *   <li>per {@code clerk_user_id}, il legame esplicito e stabile;
 *   <li>solo se il primo fallisce, per email — e in quel caso il legame viene
 *       scritto, così la volta dopo si passa dalla strada buona.
 * </ol>
 *
 * <p>L'abbinamento per email è un ripiego per il primo accesso, non un criterio
 * di sicurezza: in Clerk l'email si cambia, e due persone possono condividerne
 * una. Per questo viene usato una volta sola e solo se l'email risulta
 * verificata lato Clerk.
 *
 * @since 0.2.0
 */
@Service
public class ClerkIdentityService {

    private static final Logger logger = LoggerFactory.getLogger(ClerkIdentityService.class);

    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final UserRepository userRepository;

    public ClerkIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @return l'anagrafica di chi sta chiamando, vuota se non è collegata a
     *         nessuna persona conosciuta dal gestionale.
     */
    @Transactional
    public Optional<User> resolve(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        String clerkUserId = jwt.getSubject();
        if (clerkUserId == null || clerkUserId.isBlank()) {
            return Optional.empty();
        }

        Optional<User> linked = userRepository.findByClerkUserId(clerkUserId);
        if (linked.isPresent()) {
            return linked;
        }

        return linkByVerifiedEmail(jwt, clerkUserId);
    }

    private Optional<User> linkByVerifiedEmail(Jwt jwt, String clerkUserId) {
        String email = jwt.getClaimAsString(EMAIL_CLAIM);
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        // Un'email non verificata è una dichiarazione, non una prova: chiunque
        // può registrarsi su Clerk dichiarando l'indirizzo di un socio.
        Boolean verified = jwt.getClaim(EMAIL_VERIFIED_CLAIM);
        if (!Boolean.TRUE.equals(verified)) {
            logger.warn("Accesso con email non verificata, nessun abbinamento: {}", clerkUserId);
            return Optional.empty();
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isEmpty()) {
            return Optional.empty();
        }

        User user = byEmail.get();
        if (user.getClerkUserId() != null) {
            // Già legata a un'altra identità Clerk: non si sovrascrive, si nega.
            logger.warn("Anagrafica {} già collegata a un'altra identità Clerk", user.getId());
            return Optional.empty();
        }

        user.setClerkUserId(clerkUserId);
        userRepository.save(user);
        logger.info("Anagrafica {} collegata all'identità Clerk al primo accesso", user.getId());
        return Optional.of(user);
    }
}
