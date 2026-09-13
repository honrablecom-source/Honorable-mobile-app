# Windows VM provider comparison

Prices change by region and date; these are public US-region on-demand/list prices checked 2026-09-05 and exclude tax, persistent storage, outbound traffic, and the separate small control-plane host.

| Provider / practical size | Resources | Windows | Approx. compute cost | Operational fit |
|---|---|---|---:|---|
| Paperspace P4000 | 8 vCPU, 30 GB RAM, 8 GB dedicated GPU | Included | $0.51/hour; about $372 at 730 hours | Lowest published price here for a dedicated-GPU Windows desktop; starts at 50 GB SSD. Confirm region capacity and current desktop-product availability. |
| AWS EC2 g4ad.xlarge | 4 vCPU, 16 GiB, AMD GPU | License charge applies | $0.379/hour Linux base plus Windows; approximately $0.563/hour before disk using AWS's stated $0.046/vCPU-hour Windows rate | Mature start/stop, persistent EBS, IAM, budgets, and automation. Verify that the chosen region/image supports the current Windows AMD driver and Roblox. |
| Google Cloud g2-standard-4 | 4 vCPU, 16 GiB, NVIDIA L4 | $0.046/vCPU-hour | $0.7068 base plus about $0.184 Windows, roughly $0.891/hour before disk | Strong automation and workstation support, but materially more expensive for one Roblox session. |
| Paperspace Standard | 4 vCPU, 8 GB RAM, 512 MB virtual GPU | Included | Listed at $35/month / $0.05 effective hourly | Cheapest trial candidate, but shared graphics may be insufficient for smooth Roblox plus remote encoding; benchmark the exact experience before calling it production-ready. |

Sources: [Paperspace pricing](https://www.paperspace.com/pricing), [AWS G4 instance specifications and base prices](https://aws.amazon.com/ec2/instance-types/g4/), [AWS on-demand Windows licensing notes](https://aws.amazon.com/ec2/pricing/on-demand/), [Google G2 pricing](https://cloud.google.com/products/compute/pricing/accelerator-optimized), and [Google Windows licensing](https://cloud.google.com/products/compute/pricing/general-purpose).

## Recommendation

Use a Paperspace P4000 with an 80 GB persistent disk when its region latency and capacity are acceptable. It is the lowest clearly dedicated-GPU Windows option in this comparison and includes Windows licensing. For stronger infrastructure automation and security controls, use AWS `g4ad.xlarge` with an 80 GB gp3 EBS volume; the small premium is often justified by VPC security groups, IAM, snapshots, budgets, and reliable stop/start APIs.

Start on demand and stop only after an explicit user action or a conservative configured idle policy that is separate from browser connectivity. Stopping compute should retain the persistent system disk. Keep the VM geographically near the player and gateway; target under 50 ms round-trip latency, 15 Mbps sustained download to the client, and stable upload from the VM. Benchmark Roblox frame rate, Guacamole latency, and GPU encoder behavior before committing monthly.
