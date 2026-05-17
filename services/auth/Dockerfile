# Build stage
FROM golang:1.25-alpine AS build
WORKDIR /src

# Layer module download separately for cache reuse.
COPY go.mod go.sum ./
RUN go mod download

COPY proto ./proto
COPY services/auth ./services/auth

RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -o /out/auth ./services/auth/cmd/server

# Runtime stage
FROM alpine:3.20
RUN adduser -D -u 10001 vendex
COPY --from=build /out/auth /usr/local/bin/auth
USER vendex
ENTRYPOINT ["/usr/local/bin/auth"]
