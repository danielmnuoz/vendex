# Build stage
FROM golang:1.25-alpine AS build
WORKDIR /src

COPY go.mod go.sum ./
RUN go mod download

COPY proto ./proto
COPY services/card-catalog ./services/card-catalog

RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -o /out/card-catalog ./services/card-catalog/cmd/server

# Runtime stage
FROM alpine:3.20
RUN adduser -D -u 10001 vendex
COPY --from=build /out/card-catalog /usr/local/bin/card-catalog
USER vendex
ENTRYPOINT ["/usr/local/bin/card-catalog"]
