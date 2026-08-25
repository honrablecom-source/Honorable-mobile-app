import XCTest
@testable import Honorable

final class HonorableTests: XCTestCase {
    func testTermsFlagsRenewalAndArbitration() {
        let result = TermsAnalyzer().analyze("This subscription has automatic renewal. Disputes require arbitration.")
        XCTAssertEqual(result.risk, .medium)
        XCTAssertEqual(result.watchOut.count, 2)
    }
    func testCompatibilityIsPinned() { XCTAssertEqual(IndexCompatibility().dimension, 512) }
}
