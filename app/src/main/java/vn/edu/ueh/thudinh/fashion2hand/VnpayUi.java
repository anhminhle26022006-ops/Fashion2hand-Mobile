package vn.edu.ueh.thudinh.fashion2hand;

final class VnpayUi {
    static String queryFailure(String code) {
        return queryFailure(code,"");
    }
    static String queryFailure(String code,String reason) {
        if("QUERY_RATE_LIMITED".equals(reason))return "VNPAY đang giới hạn tra cứu. Vui lòng chờ 5 phút rồi bấm Kiểm tra lại. Nếu VNPAY đã báo thành công, đừng thanh toán lại.";
        if("RESOURCE_EXHAUSTED".equals(code))return "Chưa thể tra cứu tiếp. Vui lòng chờ 60 giây rồi bấm Kiểm tra lại. Nếu VNPAY đã báo thành công, đừng thanh toán lại.";
        if("DATA_LOSS".equals(code))return "Chưa xác minh được chữ ký kết quả từ VNPAY. Cần kiểm tra kết nối thanh toán; đừng thanh toán lại.";
        if("FAILED_PRECONDITION".equals(code))return "Cấu hình đối soát VNPAY cần được kiểm tra. Nếu bạn đã trả bằng thẻ test, đừng thanh toán lại.";
        return "Chưa xác minh được kết quả vì kết nối đối soát VNPAY đang lỗi hoặc quá thời gian chờ. Nếu VNPAY đã báo thành công, đừng thanh toán lại. Bấm Kiểm tra lại sau 60 giây.";
    }
    static String message(boolean confirmed,boolean pending,boolean review,String queryIssue) {
        if(review)return "Kết quả thanh toán đang được kiểm tra. Vui lòng xem lại đơn hàng sau.";
        if(confirmed)return "Thanh toán thành công.\nBạn đã hoàn tất đặt hàng.";
        if(!queryIssue.isEmpty())return queryIssue;
        if(pending)return queryIssue.isEmpty()?"Đang chờ kết quả thanh toán đã xác minh từ VNPAY.":queryIssue;
        return "Đơn hàng chưa hoàn tất thanh toán. Xem chi tiết đơn để tiếp tục.";
    }
}
