package io.github.m4gshm.storage;

public record Page(int num, int size) {
    public static void validatePaging(Integer num, int size) {
        if (num != null && num < 0) {
            throw new IllegalArgumentException("page.num cannot be less than 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("page.size must be more than 0");
        }
    }

    public static int getSize(Page page) {
        return page != null ? page.size() : 100;
    }

    public static int getNum(Page page) {
        return page != null ? page.num() : 0;
    }
}
