package de.jamsintown.text;

import de.jamsintown.bild.BildService;
import de.jamsintown.user.UserService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.ForbiddenException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class TextService {
    private final UserService userService;
    private final BildService bildService;

    @Inject
    public TextService(UserService userService, BildService bildService) {
        this.userService = userService;
        this.bildService = bildService;
    }

    public Uni<List<Text>> listForUser() {
        return userService.getCurrentUser()
                .chain(user -> bildService.listForUser()
                        .chain(bilder -> {
                            List<Long> textIds = bilder.stream().map(bild -> bild.text.id).toList();
                            return Text.find("id IN ?1", textIds).list();
                        }));

    }

    public Uni<List<Text>> listAll() {
        return Text.listAll(Sort.by("created").descending());
    }

    @WithTransaction
    public Uni<Text> create(Text text) {
        return userService.getCurrentUser()
                .chain(user -> {
                    text.user = user;
                    return text.persistAndFlush();
                });
    }

    public Uni<Text> findByIdForUser(Long id) {
        return bildService.listForUser()
                .chain(bilder -> {
                    List<Long> textIds = bilder.stream().map(bild -> bild.text.id).toList();
                    if (!textIds.contains(id)) {
                        return Uni.createFrom().failure(new ForbiddenException("Access denied to text with id: " + id));
                    }
                    return Text.find("id = ?1 AND id IN ?2", id, textIds).firstResult();
                });
    }
}
