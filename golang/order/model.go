package order

type Status string

const (
	StatusCreating     Status = "CREATING"
	StatusCreated      Status = "CREATED"
	StatusApproving    Status = "APPROVING"
	StatusApproved     Status = "APPROVED"
	StatusReleasing    Status = "RELEASING"
	StatusReleased     Status = "RELEASED"
	StatusInsufficient Status = "INSUFFICIENT"
	StatusCancelling   Status = "CANCELLING"
	StatusCancelled    Status = "CANCELLED"
)
