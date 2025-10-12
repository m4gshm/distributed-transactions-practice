package grpc

import (
	"embed"
	"io/fs"
)

//go:embed gen/apidocs.swagger.json
var swaggerJson embed.FS

var SwaggerJson = func() fs.FS {
	f, err := fs.Sub(swaggerJson, "gen")
	if err != nil {
		panic(err)
	}
	return f
}()

