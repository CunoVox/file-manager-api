package cloud.haovo.filemanager.security;

import cloud.haovo.filemanager.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(@Value("${app.security.jwt-secret:change-this-development-secret-key-32-chars}") String secret,
            @Value("${app.security.jwt-expiration-ms:900000}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", user.safeRoles().stream().map(Enum::name).collect(Collectors.toList()));
        return Jwts.builder().setClaims(claims).setSubject(user.getEmail())
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(signingKey, SignatureAlgorithm.HS256).compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isValid(String token, UserDetails details) {
        try {
            return extractUsername(token).equalsIgnoreCase(details.getUsername()) && !isExpired(token);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody());
    }
}