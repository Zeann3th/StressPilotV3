FROM postgres:17-alpine AS builder

RUN apk add --no-cache \
    git make gcc libc-dev postgresql17-dev \
    clang19 llvm19-libs llvm19

RUN git clone --branch v5.1.0 --depth 1 https://github.com/pgpartman/pg_partman.git /tmp/pg_partman \
    && cd /tmp/pg_partman \
    && make NO_BITCODE=1 install

FROM postgres:17-alpine

COPY --from=builder /usr/local/lib/postgresql/pg_partman_bgw.so /usr/local/lib/postgresql/
COPY --from=builder /usr/local/share/postgresql/extension/pg_partman* /usr/local/share/postgresql/extension/

CMD ["postgres", "-c", "shared_preload_libraries=pg_partman_bgw"]