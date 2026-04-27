package de.jamsintown;

import io.quarkus.security.UnauthorizedException;
import io.vertx.pgclient.PgException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.HibernateException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Objects;
import java.util.Optional;

@Provider
public class RestExceptionHandler implements ExceptionMapper<HibernateException> {

  private static final String PG_UNIQUE_VIOLATION_ERROR = "23505";

  @Provider
  public static class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
    @Override
    public Response toResponse(NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
    }
  }

  @Provider
  public static class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {
    @Override
    public Response toResponse(UnauthorizedException e) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }

  @Override
  public Response toResponse(HibernateException exception) {
    if (hasExceptionInChain(exception, ObjectNotFoundException.class)) {
      return Response.status(Response.Status.NOT_FOUND).entity(exception.getMessage()).build();
    }
    if (hasExceptionInChain(exception, StaleObjectStateException.class)
      || hasExceptionInChain(exception, ConstraintViolationException.class)
      || hasPostgresErrorCode(exception)) {
      return Response.status(Response.Status.CONFLICT).build();
    }
    return Response
      .status(Response.Status.BAD_REQUEST)
      .entity("\"" + exception.getMessage() + "\"")
      .build();
  }

  private static boolean hasExceptionInChain(Throwable throwable, Class<? extends Throwable> exceptionClass) {
    return getExceptionInChain(throwable, exceptionClass).isPresent();
  }

  private static boolean hasPostgresErrorCode(Throwable throwable) {
    return getExceptionInChain(throwable, PgException.class)
      .filter(ex -> Objects.equals(ex.getSqlState(), RestExceptionHandler.PG_UNIQUE_VIOLATION_ERROR))
      .isPresent();
  }

  private static <T extends Throwable> Optional<T> getExceptionInChain(Throwable throwable, Class<T> exceptionClass) {
    while (throwable != null) {
      if (exceptionClass.isInstance(throwable)) {
        return Optional.of(exceptionClass.cast(throwable));
      }
      throwable = throwable.getCause();
    }
    return Optional.empty();
  }
}

