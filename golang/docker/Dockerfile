# scratch is an empty image
FROM scratch
# FROM alpine:latest
ARG EXEC_NAME 

COPY ./cmd/$EXEC_NAME /app

ENTRYPOINT ["/app"]