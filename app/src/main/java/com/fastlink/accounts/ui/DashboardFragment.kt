package com.fastlink.accounts.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fastlink.accounts.R
import com.fastlink.accounts.databinding.FragmentDashboardBinding
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.viewmodel.FastLinkViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FastLinkViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainAct = activity as? MainActivity

        viewModel.dashboardStats.observe(viewLifecycleOwner) { stats ->
            val cur = viewModel.currencySymbol
            binding.tvPackagesCount.text = stats.packagesCount.toString()
            binding.tvInventoryCardsCount.text = "${stats.totalInventoryCards} كرت"
            binding.tvStoresCount.text = stats.storesCount.toString()
            binding.tvTotalSales.text = FormatUtils.formatAmount(stats.totalSales, cur)
            binding.tvTotalPayments.text = FormatUtils.formatAmount(stats.totalPayments, cur)
            binding.tvTotalDebts.text = FormatUtils.formatAmount(stats.totalDebts, cur)
            binding.tvTotalExpenses.text = FormatUtils.formatAmount(stats.totalExpenses, cur)
            binding.tvNetProfit.text = FormatUtils.formatAmount(stats.netProfit, cur)
        }

        // كل بطاقة قابلة للضغط وتنقل إلى القسم المرتبط بها
        binding.cardPackagesCount.setOnClickListener { mainAct?.navigateToSection(R.id.nav_packages) }
        binding.cardInventoryTotal.setOnClickListener { mainAct?.navigateToSection(R.id.nav_inventory) }
        binding.cardStoresCount.setOnClickListener { mainAct?.navigateToSection(R.id.nav_stores) }
        binding.cardTotalSales.setOnClickListener { mainAct?.navigateToSection(R.id.nav_reports) }
        binding.cardTotalPayments.setOnClickListener { mainAct?.navigateToSection(R.id.nav_reports) }
        binding.cardTotalDebts.setOnClickListener { mainAct?.navigateToSection(R.id.nav_stores) }
        binding.cardTotalExpenses.setOnClickListener { mainAct?.navigateToSection(R.id.nav_expenses) }
        binding.cardNetProfit.setOnClickListener { mainAct?.navigateToSection(R.id.nav_reports) }

        // الاختصارات السريعة
        binding.btnQuickAddStore.setOnClickListener { mainAct?.navigateToSection(R.id.nav_stores) }
        binding.btnQuickAddExpense.setOnClickListener { mainAct?.navigateToSection(R.id.nav_expenses) }
        binding.btnQuickAddPackage.setOnClickListener { mainAct?.navigateToSection(R.id.nav_packages) }
        binding.btnQuickViewReports.setOnClickListener { mainAct?.navigateToSection(R.id.nav_reports) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
