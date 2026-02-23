#!/usr/bin/env sh

podman rmi $(podman images -f "dangling=true" -q)
