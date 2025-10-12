package swagger

import (
	"embed"
	"io/fs"
)

var UI fs.FS = i("5.29.0")

//go:embed 5.29.0/*
var swaggerUIs embed.FS

func i(v string) fs.FS {
	f, err := fs.Sub(swaggerUIs, v)
	if err != nil {
		panic(err)
	}
	return f
}
