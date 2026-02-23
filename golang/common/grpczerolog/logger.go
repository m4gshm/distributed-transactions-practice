package grpczerolog

import (
	"fmt"

	"github.com/rs/zerolog"
)

type Logger struct {
	log zerolog.Logger
}

func New(log zerolog.Logger) Logger {
	return Logger{log: log}
}

func (l Logger) Fatal(args ...any) {
	l.log.Fatal().Msg(fmt.Sprint(args...))
}

func (l Logger) Fatalf(format string, args ...any) {
	l.log.Fatal().Msg(fmt.Sprintf(format, args...))
}

func (l Logger) Fatalln(args ...any) {
	l.Fatal(args...)
}

func (l Logger) Error(args ...any) {
	l.log.Error().Msg(fmt.Sprint(args...))
}

func (l Logger) Errorf(format string, args ...any) {
	l.log.Error().Msg(fmt.Sprintf(format, args...))
}

func (l Logger) Errorln(args ...any) {
	l.Error(args...)
}

func (l Logger) Info(args ...any) {
	l.log.Info().Msg(fmt.Sprint(args...))
}

func (l Logger) Infof(format string, args ...any) {
	l.log.Info().Msg(fmt.Sprintf(format, args...))
}

func (l Logger) Infoln(args ...any) {
	l.Info(args...)
}

func (l Logger) Warning(args ...any) {
	l.log.Warn().Msg(fmt.Sprint(args...))
}

func (l Logger) Warningf(format string, args ...any) {
	l.log.Warn().Msg(fmt.Sprintf(format, args...))
}

func (l Logger) Warningln(args ...any) {
	l.Warning(args...)
}

func (l Logger) Print(args ...any) {
	l.log.Info().Msg(fmt.Sprint(args...))
}

func (l Logger) Printf(format string, args ...any) {
	l.log.Info().Msg(fmt.Sprintf(format, args...))
}

func (l Logger) Println(args ...any) {
	l.Print(args...)
}

func (l Logger) V(level int) bool {
	currentLevel := l.log.GetLevel()
	requestedLevel := zerolog.Level(level)
	ok := currentLevel == requestedLevel
	return ok
}
