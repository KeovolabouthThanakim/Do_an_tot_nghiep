package com.kosis.shop.service;

import com.kosis.shop.entity.Positioned;

import java.util.Collections;
import java.util.List;

/** Sắp xếp thứ tự hiển thị: đổi chỗ với phần tử kế bên rồi đánh lại số thứ tự 0..n-1. */
final class MenuOrdering {

    private MenuOrdering() {
    }

    /**
     * @param sorted danh sách (sửa được) đã xếp theo thứ tự hiện tại
     * @param delta  -1 = lên, +1 = xuống
     * @return true nếu có đổi chỗ (false: đã ở đầu / cuối danh sách)
     */
    static <T extends Positioned> boolean move(List<T> sorted, int index, int delta) {
        int target = index + delta;
        boolean moved = index >= 0 && index < sorted.size() && target >= 0 && target < sorted.size();
        if (moved) {
            Collections.swap(sorted, index, target);
        }
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setSortOrder(i);
        }
        return moved;
    }
}
