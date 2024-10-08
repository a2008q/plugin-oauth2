package run.halo.oauth;

import static run.halo.app.core.extension.User.GROUP;
import static run.halo.app.core.extension.User.KIND;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.RoleBinding;
import run.halo.app.core.extension.User;
import run.halo.app.extension.GroupKind;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * A default implementation for finding the UserDetails by username.
 *
 * @author guqing
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class DefaultUserDetailsService implements ReactiveUserDetailsService {
    public static final String AUTHENTICATED_ROLE_NAME = "authenticated";
    public static final String ANONYMOUS_ROLE_NAME = "anonymous";

    private final ReactiveExtensionClient client;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return this.client.fetch(User.class, username)
            .flatMap(user -> {
                var subject = new RoleBinding.Subject(KIND, username, GROUP);
                return listRoleRefs(subject)
                    .filter(this::isRoleRef)
                    .map(RoleBinding.RoleRef::getName)
                        .concatWithValues(AUTHENTICATED_ROLE_NAME, ANONYMOUS_ROLE_NAME)
                    .collectList()
                    .map(roleNames -> org.springframework.security.core.userdetails.User.builder()
                        .username(username)
                        .password(user.getSpec().getPassword())
                        .roles(roleNames.toArray(new String[0]))
                        .build());
            });
    }

    public Flux<RoleBinding.RoleRef> listRoleRefs(RoleBinding.Subject subject) {
        return client.list(RoleBinding.class,
                binding -> binding.getSubjects().contains(subject),
                null)
            .map(RoleBinding::getRoleRef);
    }

    private boolean isRoleRef(RoleBinding.RoleRef roleRef) {
        var roleGvk = new Role().groupVersionKind();
        var gk = new GroupKind(roleRef.getApiGroup(), roleRef.getKind());
        return gk.equals(roleGvk.groupKind());
    }
}
