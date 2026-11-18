package com.kosis.shop.entity;

/** Phần tử có thứ tự hiển thị do ADMIN sắp xếp (danh mục, liên kết menu). */
public interface Positioned {
    int getSortOrder();

    void setSortOrder(int sortOrder);
}
