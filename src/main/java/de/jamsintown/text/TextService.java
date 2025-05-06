package de.jamsintown.text;

import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class TextService {
    private final UserService userService;

    @Inject
    public TextService(UserService userService) {
        this.userService = userService;
    }

    public Uni<List<Text>> listForUser() {

        return userService.getCurrentUser()
                .chain(user -> Text.find("user", user).list());
    }

    public Uni<List<Text>> listAll() {
        return Text.listAll(Sort.by("created").descending());
    }

    @WithTransaction
    public Uni<Text> create(Text text) {
        return userService.getCurrentUser()
                .chain(user -> {
                    return text.persistAndFlush();
                });
    }
}
