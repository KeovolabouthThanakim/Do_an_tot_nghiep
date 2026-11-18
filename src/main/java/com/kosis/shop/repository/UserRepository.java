package com.kosis.shop.repository;

import com.kosis.shop.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Kiểm tra trùng KHÔNG phân biệt hoa/thường ("Admin" và "admin" là cùng 1 tên) */
    /** Đăng nhập bằng email (không phân biệt hoa/thường) */
    List<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** Trùng tên đăng nhập với tài khoản KHÁC (dùng khi đổi tên đăng nhập) */
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    /** Trùng email với tài khoản KHÁC (dùng khi sửa thông tin) */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Đếm tài khoản theo vai trò (USER = khách hàng) */
    long countByRole(String role);

    /** Danh sách tài khoản theo vai trò, mới nhất lên đầu (trang khách hàng) */
    List<User> findByRoleOrderByIdDesc(String role);

    /** Danh sách tài khoản thuộc các vai trò (trang nhân viên: ADMIN + NHANVIEN) */
    List<User> findByRoleInOrderByRoleAscUsernameAsc(Collection<String> roles);

    /** Đếm tài khoản theo vai trò + trạng thái (dùng để không cho khoá/hạ quyền ADMIN cuối cùng) */
    long countByRoleAndEnabled(String role, boolean enabled);
}
